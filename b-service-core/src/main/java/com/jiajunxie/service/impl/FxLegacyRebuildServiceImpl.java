package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.FxDetail;
import com.jiajunxie.entity.FxPosition;
import com.jiajunxie.entity.FxPositionMovement;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.FxDetailMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.mapper.FxPositionMovementMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.FxLegacyRebuildService;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.FxLegacyRebuildReportVo;
import com.jiajunxie.vo.FxLegacyRebuildRequestVo;
import com.jiajunxie.vo.FxLegacyRebuildStepVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FxLegacyRebuildServiceImpl implements FxLegacyRebuildService {
  private static final int MONEY_SCALE = 4;
  private static final int RATE_SCALE = 10;
  private static final ZoneId ACCOUNTING_ZONE = ZoneId.of("Asia/Tokyo");

  private final AccountMapper accountMapper;
  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final FxDetailMapper fxDetailMapper;
  private final FxPositionMapper positionMapper;
  private final FxPositionMovementMapper movementMapper;
  private final ExchangeRateService exchangeRateService;
  private final CurrencyInventoryRebuildMapper currencyInventoryRebuildMapper;

  @Override
  @Transactional(readOnly = true)
  public List<FxLegacyRebuildReportVo> preview(FxLegacyRebuildRequestVo request) {
    return rebuild(request, false);
  }

  @Override
  @Transactional
  public List<FxLegacyRebuildReportVo> apply(FxLegacyRebuildRequestVo request) {
    return rebuild(request, true);
  }

  private List<FxLegacyRebuildReportVo> rebuild(FxLegacyRebuildRequestVo request, boolean apply) {
    if (currencyInventoryRebuildMapper.selectInventoryCutoverAppliedAt() != null) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "FX Cost Inventory V3 已完成 cutover；Legacy FxPosition rebuild 已退役");
    }
    if (request == null || request.accountIds() == null || request.accountIds().isEmpty()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "至少指定一个需要重建的外币账户");
    }
    Set<String> openingIds = new HashSet<>(request.openingBalanceTransactionIds() == null ? List.of() : request.openingBalanceTransactionIds());
    int lookAheadDays = request.openingRateLookAheadDays() == null ? 7 : request.openingRateLookAheadDays();
    if (lookAheadDays < 0 || lookAheadDays > 31) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "期初余额汇率向后查找天数必须在 0~31 之间");
    }

    List<FxLegacyRebuildReportVo> reports = new ArrayList<>();
    for (String accountId : request.accountIds()) {
      reports.add(rebuildAccount(accountId, openingIds, lookAheadDays, apply));
    }
    return reports;
  }

  private FxLegacyRebuildReportVo rebuildAccount(String accountId, Set<String> openingIds, int lookAheadDays, boolean apply) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "外币账户不存在：" + accountId);
    if (!"ASSET".equals(norm(account.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "仅支持资产账户 FX 历史重建：" + account.getAccountName());
    }
    String currency = norm(account.getCurrency());
    FxPosition current = positionMapper.selectByAccountId(accountId);
    String costCurrency = resolveCostCurrency(account, current);
    if (currency.equals(costCurrency)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户币种与 FX 成本币种相同，不需要 FX Position：" + account.getAccountName());
    }
    State state = new State();
    List<PendingMovement> pending = new ArrayList<>();
    List<FxLegacyRebuildStepVo> steps = new ArrayList<>();

    for (String txId : entryMapper.selectTransactionIdsByAccountIdChronological(accountId)) {
      Transaction tx = transactionMapper.selectById(txId);
      if (tx == null) continue;
      TransactionEntry accountEntry = entryMapper.selectByTransactionId(txId).stream()
          .filter(e -> accountId.equals(e.getAccountId()))
          .reduce((a,b) -> { throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "同一外币账户在单笔交易中存在多个分录，历史重建暂不支持：" + txId); })
          .orElse(null);
      if (accountEntry == null) continue;

      String type = norm(tx.getTransactionType());
      if ("TRANSFER".equals(type) || "REFUND".equals(type) || type.startsWith("LOAN_")) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史重建发现需要专用成本恢复的交易类型 " + type + "，请单独审计：" + txId);
      }

      if ("EXCHANGE".equals(type)) {
        applyExchange(account, costCurrency, tx, accountEntry, state, pending, steps);
      } else if (accountEntry.getAmount().signum() > 0) {
        boolean opening = openingIds.contains(txId);
        RateChoice rate = acquireRate(tx.getTransactionTime(), currency, costCurrency, opening, lookAheadDays);
        BigDecimal quantity = accountEntry.getAmount();
        BigDecimal cost = money(quantity.multiply(rate.rate()));
        acquire(state, quantity, cost);
        String movementType = opening ? "OPENING_BALANCE_ACQUIRE" : "INCOME_ACQUIRE";
        addMovement(pending, steps, tx, movementType, accountId, currency, quantity, cost, costCurrency, rate.rate(), rate.source(), null, state);
      } else if (accountEntry.getAmount().signum() < 0) {
        BigDecimal quantity = accountEntry.getAmount().abs();
        BigDecimal cost = dispose(state, quantity, txId);
        RateChoice valuation = tryRate(tx.getTransactionTime(), currency, costCurrency);
        BigDecimal gain = valuation == null ? null : money(quantity.multiply(valuation.rate()).subtract(cost));
        addMovement(pending, steps, tx, "EXPENSE_DISPOSE", accountId, currency, quantity.negate(), cost.negate(), costCurrency,
            valuation == null ? null : valuation.rate(), valuation == null ? null : valuation.source(), gain, state);
      }
    }

    BigDecimal rebuiltQty = money(state.quantity);
    BigDecimal rebuiltCost = money(state.cost);
    BigDecimal rebuiltAvg = rebuiltQty.signum() == 0 ? BigDecimal.ZERO.setScale(RATE_SCALE) : state.cost.divide(state.quantity, RATE_SCALE, RoundingMode.HALF_UP);
    if (rebuiltQty.compareTo(account.getTotalAmount()) != 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT,
          "历史重放数量与账户余额不一致：" + account.getAccountName() + " account=" + account.getTotalAmount() + " rebuilt=" + rebuiltQty);
    }

    if (apply) {
      movementMapper.deleteByAccountId(accountId);
      positionMapper.delete(Wrappers.<FxPosition>lambdaQuery().eq(FxPosition::getAccountId, accountId));
      if (rebuiltQty.signum() > 0) {
        if (positionMapper.upsertBuyPosition(UUID.randomUUID().toString(), accountId, currency, rebuiltQty, rebuiltCost, costCurrency) != 1) {
          throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "重建 FX Position 失败：" + account.getAccountName());
        }
      }
      for (PendingMovement p : pending) movementMapper.insert(p.entity());
    }

    BigDecimal currentQty = current == null ? BigDecimal.ZERO : current.getQuantity();
    BigDecimal currentCost = current == null ? BigDecimal.ZERO : current.getTotalCost();
    return new FxLegacyRebuildReportVo(accountId, account.getAccountName(), currency, costCurrency,
        account.getTotalAmount(), currentQty, currentCost, rebuiltQty, rebuiltCost, rebuiltAvg,
        rebuiltQty.subtract(currentQty), rebuiltCost.subtract(currentCost), steps);
  }


  /**
   * FX cost currency is a property of the position, not of the account hierarchy.
   * Parent/child accounts are organizational only. For legacy data prefer the existing
   * position's cost currency; if no position exists, infer one unambiguous counterpart
   * currency from the account's historical EXCHANGE facts.
   */
  private String resolveCostCurrency(Account account, FxPosition current) {
    if (current != null && !norm(current.getCostCurrency()).isBlank()) {
      return norm(current.getCostCurrency());
    }

    Set<String> candidates = new HashSet<>();
    for (String txId : entryMapper.selectTransactionIdsByAccountIdChronological(account.getAccountId())) {
      Transaction tx = transactionMapper.selectById(txId);
      if (tx == null || !"EXCHANGE".equals(norm(tx.getTransactionType()))) continue;
      FxDetail fx = fxDetailMapper.selectById(txId);
      if (fx == null) continue;
      if (account.getAccountId().equals(fx.getTargetAccountId())) {
        candidates.add(norm(fx.getSourceCurrency()));
      } else if (account.getAccountId().equals(fx.getSourceAccountId())) {
        candidates.add(norm(fx.getTargetCurrency()));
      }
    }
    candidates.remove(norm(account.getCurrency()));
    candidates.remove("");
    if (candidates.size() == 1) return candidates.iterator().next();
    if (candidates.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "无法确定独立外币账户的 FX 成本币种；请先建立 FX Position 或提供可推导的历史兑换：" + account.getAccountName());
    }
    throw new BusinessException(
        CommonErrorCode.DATA_CONFLICT,
        "历史兑换存在多个成本币种，无法自动重建；请分币种审计：" + account.getAccountName() + " " + candidates);
  }

  private void applyExchange(Account account, String costCurrency, Transaction tx, TransactionEntry entry, State state,
      List<PendingMovement> pending, List<FxLegacyRebuildStepVo> steps) {
    FxDetail fx = fxDetailMapper.selectById(tx.getTransactionId());
    if (fx == null) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "EXCHANGE 缺少 FxDetail：" + tx.getTransactionId());
    if (account.getAccountId().equals(fx.getTargetAccountId())) {
      if (!costCurrency.equals(norm(fx.getSourceCurrency()))) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史 Cross-FX 暂不支持自动重建：" + tx.getTransactionId());
      }
      BigDecimal quantity = fx.getTargetAmount();
      BigDecimal cost = fx.getSourceAmount();
      if (fx.getFeeAmount() != null && fx.getFeeAmount().signum() > 0) {
        if (costCurrency.equals(norm(fx.getFeeCurrency()))) cost = cost.add(fx.getFeeAmount());
        else if (norm(account.getCurrency()).equals(norm(fx.getFeeCurrency()))) quantity = quantity.subtract(fx.getFeeAmount());
      }
      acquire(state, quantity, cost);
      BigDecimal rate = cost.divide(quantity, RATE_SCALE, RoundingMode.HALF_UP);
      addMovement(pending, steps, tx, "FX_BUY", account.getAccountId(), account.getCurrency(), quantity, money(cost), costCurrency, rate, "FX_EXECUTED", null, state);
    } else if (account.getAccountId().equals(fx.getSourceAccountId())) {
      if (!costCurrency.equals(norm(fx.getTargetCurrency()))) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史 Cross-FX 暂不支持自动重建：" + tx.getTransactionId());
      }
      BigDecimal quantity = fx.getSourceAmount();
      if (fx.getFeeAmount() != null && fx.getFeeAmount().signum() > 0 && norm(account.getCurrency()).equals(norm(fx.getFeeCurrency()))) {
        quantity = quantity.add(fx.getFeeAmount());
      }
      BigDecimal cost = dispose(state, quantity, tx.getTransactionId());
      BigDecimal proceeds = fx.getTargetAmount();
      if (fx.getFeeAmount() != null && fx.getFeeAmount().signum() > 0 && costCurrency.equals(norm(fx.getFeeCurrency()))) {
        proceeds = proceeds.subtract(fx.getFeeAmount());
      }
      BigDecimal gain = money(proceeds.subtract(cost));
      BigDecimal rate = proceeds.divide(quantity, RATE_SCALE, RoundingMode.HALF_UP);
      addMovement(pending, steps, tx, "FX_SELL", account.getAccountId(), account.getCurrency(), quantity.negate(), cost.negate(), costCurrency, rate, "FX_EXECUTED", gain, state);
    }
  }

  private RateChoice acquireRate(Instant time, String currency, String costCurrency, boolean opening, int lookAheadDays) {
    RateChoice direct = tryRate(time, currency, costCurrency);
    if (direct != null) return direct;
    if (!opening) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "外币收入缺少交易日有效历史汇率：" + currency + "/" + costCurrency);
    LocalDate date = localDate(time);
    for (int i = 1; i <= lookAheadDays; i++) {
      try {
        ExchangeRateVo vo = exchangeRateService.effective(date.plusDays(i), currency, costCurrency, null);
        if (vo.rateDate() != null && !vo.rateDate().isBefore(date)) {
          return new RateChoice(vo.exchangeRate(), "OPENING_PROXY:" + vo.rateSourceCode() + "@" + vo.rateDate());
        }
      } catch (BusinessException ignored) { }
    }
    throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "期初余额附近没有可用历史汇率：" + currency + "/" + costCurrency + " " + date);
  }

  private RateChoice tryRate(Instant time, String currency, String costCurrency) {
    try {
      ExchangeRateVo vo = exchangeRateService.effective(localDate(time), currency, costCurrency, null);
      return new RateChoice(vo.exchangeRate(), vo.rateSourceCode());
    } catch (BusinessException ex) {
      return null;
    }
  }

  private LocalDate localDate(Instant time) {
    return (time == null ? Instant.now() : time).atZone(ACCOUNTING_ZONE).toLocalDate();
  }

  private void acquire(State s, BigDecimal quantity, BigDecimal cost) {
    s.quantity = s.quantity.add(quantity);
    s.cost = s.cost.add(cost);
  }

  private BigDecimal dispose(State s, BigDecimal quantity, String txId) {
    if (quantity.compareTo(s.quantity) > 0) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史重建发生超卖：" + txId);
    boolean close = quantity.compareTo(s.quantity) == 0;
    BigDecimal cost = close ? s.cost : money(quantity.multiply(s.average()));
    s.quantity = close ? BigDecimal.ZERO : s.quantity.subtract(quantity);
    s.cost = close ? BigDecimal.ZERO : s.cost.subtract(cost);
    return cost;
  }

  private void addMovement(List<PendingMovement> pending, List<FxLegacyRebuildStepVo> steps, Transaction tx,
      String type, String accountId, String currency, BigDecimal quantityDelta, BigDecimal costDelta,
      String costCurrency, BigDecimal rate, String rateSource, BigDecimal gain, State state) {
    FxPositionMovement m = new FxPositionMovement();
    m.setMovementId(UUID.randomUUID().toString());
    m.setTransactionId(tx.getTransactionId());
    m.setAccountId(accountId);
    m.setMovementOrder(0);
    m.setMovementTypeCode(type);
    m.setCurrency(norm(currency));
    m.setQuantityDelta(quantityDelta);
    m.setCostDelta(costDelta);
    m.setCostCurrency(norm(costCurrency));
    m.setValuationRate(rate);
    m.setRealizedGainLoss(gain);
    m.setGainLossCurrency(gain == null ? null : norm(costCurrency));
    m.setCreatedAt(tx.getTransactionTime());
    pending.add(new PendingMovement(m));
    steps.add(new FxLegacyRebuildStepVo(tx.getTransactionTime(), tx.getTransactionId(), tx.getTransactionType(), type,
        quantityDelta, costDelta, rate, rateSource, gain, money(state.quantity), money(state.cost), state.quantity.signum()==0 ? BigDecimal.ZERO.setScale(RATE_SCALE) : state.average()));
  }

  private BigDecimal money(BigDecimal v) { return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP); }
  private String norm(String v) { return v == null ? "" : v.trim().toUpperCase(Locale.ROOT); }

  private static final class State {
    BigDecimal quantity = BigDecimal.ZERO;
    BigDecimal cost = BigDecimal.ZERO;
    BigDecimal average() { return quantity.signum()==0 ? BigDecimal.ZERO : cost.divide(quantity, RATE_SCALE, RoundingMode.HALF_UP); }
  }
  private record RateChoice(BigDecimal rate, String source) {}
  private record PendingMovement(FxPositionMovement entity) {}
}
