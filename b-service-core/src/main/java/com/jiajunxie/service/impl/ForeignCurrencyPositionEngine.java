package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.FxPosition;
import com.jiajunxie.entity.FxPositionMovement;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.mapper.FxPositionMovementMapper;
import com.jiajunxie.service.ExchangeRateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility gateway for ordinary FX-cost lifecycle writes.
 *
 * <p>Before the V3 cutover marker exists it keeps the legacy single-cost-currency FxPosition model
 * synchronized. After cutover it delegates entirely to {@link CurrencyInventoryLifecycleEngine};
 * ordinary income/refund then stays non-FX and no longer creates a synthetic legacy cost position.
 */
@Component
@RequiredArgsConstructor
public class ForeignCurrencyPositionEngine {
  private static final int MONEY_SCALE = 4;
  private static final ZoneId ACCOUNTING_ZONE = ZoneId.of("Asia/Tokyo");

  private final AccountMapper accountMapper;
  private final FxPositionMapper positionMapper;
  private final FxPositionMovementMapper movementMapper;
  private final ExchangeRateService exchangeRateService;
  private final FxAccountingPolicy accountingPolicy;
  private final CurrencyInventoryLifecycleEngine currencyInventoryLifecycleEngine;

  @Transactional
  public void apply(
      String transactionId,
      String transactionType,
      Instant transactionTime,
      List<TransactionEntry> entries) {
    if (entries == null || entries.isEmpty()) return;
    if (currencyInventoryLifecycleEngine.isActive()) {
      currencyInventoryLifecycleEngine.applyTransaction(
          transactionId, transactionType, transactionTime, entries);
      return;
    }
    String type = normalize(transactionType);

    if ("TRANSFER".equals(type)) {
      applyTransfer(transactionId, transactionTime, entries);
      return;
    }

    int order = 0;
    for (TransactionEntry entry : entries) {
      Account account = accountMapper.selectById(entry.getAccountId());
      if (account == null || !"ASSET".equals(normalize(account.getAccountClassCode()))) continue;
      FxPosition currentPosition = positionMapper.selectByAccountId(account.getAccountId());
      boolean foreignToDefault =
          !normalize(account.getCurrency()).equals(accountingPolicy.defaultCostCurrency());
      if (currentPosition == null && !foreignToDefault) {
        // Base accounting currency does not need an FX Position.
        continue;
      }

      if (entry.getAmount().signum() < 0) {
        dispose(
            transactionId,
            order++,
            movementType(type, false),
            account,
            entry.getAmount().abs(),
            transactionTime);
      } else if (entry.getAmount().signum() > 0) {
        acquire(
            transactionId,
            order++,
            movementType(type, true),
            account,
            entry.getAmount(),
            transactionTime);
      }
    }
  }

  public boolean hasMovements(String transactionId) {
    return transactionId != null && movementMapper.countByTransactionId(transactionId) > 0;
  }

  public boolean tracks(Account account) {
    return account != null
        && "ASSET".equals(normalize(account.getAccountClassCode()))
        && positionMapper.selectByAccountId(account.getAccountId()) != null;
  }

  public void assertLegacyMutationSafe(String transactionId, List<TransactionEntry> entries) {
    if (currencyInventoryLifecycleEngine.isActive()) {
      currencyInventoryLifecycleEngine.assertMutationSafe(transactionId, entries);
      return;
    }
    if (hasMovements(transactionId) || entries == null || entries.isEmpty()) return;
    for (TransactionEntry entry : entries) {
      Account account = accountMapper.selectById(entry.getAccountId());
      if (tracks(account)) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "该历史外币交易创建于 FX 生命周期修复之前，缺少成本变动记录；请先执行 V4 历史 FX 数据修复后再修改/删除");
      }
    }
  }

  @Transactional
  public boolean reverse(String transactionId) {
    if (currencyInventoryLifecycleEngine.isActive()) {
      return currencyInventoryLifecycleEngine.reverse(transactionId);
    }
    List<FxPositionMovement> movements = movementMapper.selectByTransactionIdReverse(transactionId);
    if (movements == null || movements.isEmpty()) return false;

    for (FxPositionMovement movement : movements) {
      long later =
          movementMapper.countLaterMovements(
              movement.getAccountId(), transactionId, movement.getCreatedAt());
      if (later > 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "该外币交易之后已有新的持仓变动，不能直接修改/删除；请使用冲销交易保持成本链可审计");
      }
      if (movement.getQuantityDelta().signum() < 0) {
        // Reverse a disposal/transfer-out by restoring the exact historical carrying cost.
        int affected =
            positionMapper.upsertBuyPosition(
                UUID.randomUUID().toString(),
                movement.getAccountId(),
                movement.getCurrency(),
                movement.getQuantityDelta().abs(),
                movement.getCostDelta().abs(),
                movement.getCostCurrency());
        if (affected != 1) {
          throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Position 反向恢复失败");
        }
      } else {
        // Reverse an acquisition/transfer-in by removing exactly the cost introduced by it.
        FxPosition position = positionMapper.selectByAccountIdForUpdate(movement.getAccountId());
        if (position == null
            || position.getQuantity().compareTo(movement.getQuantityDelta()) < 0
            || position.getTotalCost().compareTo(movement.getCostDelta()) < 0) {
          throw new BusinessException(
              CommonErrorCode.DATA_CONFLICT,
              "FX Position 已被后续业务占用，无法直接修改/删除该历史交易");
        }
        boolean close = position.getQuantity().compareTo(movement.getQuantityDelta()) == 0;
        int affected =
            positionMapper.applySellPosition(
                movement.getAccountId(), movement.getQuantityDelta(), movement.getCostDelta(), close);
        if (affected != 1) {
          throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Position 反向扣减失败");
        }
      }
    }
    movementMapper.deleteByTransactionId(transactionId);
    return true;
  }

  private void applyTransfer(
      String transactionId, Instant transactionTime, List<TransactionEntry> entries) {
    List<TransactionEntry> assetEntries = new ArrayList<>();
    for (TransactionEntry entry : entries) {
      Account account = accountMapper.selectById(entry.getAccountId());
      if (account != null && "ASSET".equals(normalize(account.getAccountClassCode()))) {
        assetEntries.add(entry);
      }
    }
    TransactionEntry source =
        assetEntries.stream().filter(item -> item.getAmount().signum() < 0).findFirst().orElse(null);
    TransactionEntry target =
        assetEntries.stream().filter(item -> item.getAmount().signum() > 0).findFirst().orElse(null);
    if (source == null || target == null) return;

    FxPosition sourcePosition = positionMapper.selectByAccountIdForUpdate(source.getAccountId());
    if (sourcePosition == null) {
      // A base-currency or legacy untracked transfer has no FX cost to move.
      return;
    }
    BigDecimal quantity = source.getAmount().abs();
    if (quantity.compareTo(sourcePosition.getQuantity()) > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "转出金额超过该账户已追踪 FX 持仓；请先修复账户/FX Position 差异");
    }

    Account targetAccount = accountMapper.selectById(target.getAccountId());
    if (targetAccount == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "转入账户不存在");
    }
    if (!normalize(sourcePosition.getCurrency()).equals(normalize(targetAccount.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX 成本迁移要求转入账户币种一致");
    }

    // Account hierarchy is organizational only. Same-currency transfer carries the source
    // position cost basis regardless of whether either account has a parent.
    FxPosition targetPosition = positionMapper.selectByAccountId(targetAccount.getAccountId());
    if (targetPosition != null
        && !normalize(sourcePosition.getCostCurrency())
            .equals(normalize(targetPosition.getCostCurrency()))) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "两个外币账户的成本币种不同，不能通过普通转账合并持仓");
    }

    boolean close = quantity.compareTo(sourcePosition.getQuantity()) == 0;
    BigDecimal cost =
        close
            ? sourcePosition.getTotalCost()
            : quantity
                .multiply(sourcePosition.getAverageCostRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    if (positionMapper.applySellPosition(source.getAccountId(), quantity, cost, close) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "转出账户 FX Position 更新失败");
    }
    if (positionMapper.upsertBuyPosition(
            UUID.randomUUID().toString(),
            target.getAccountId(),
            normalize(sourcePosition.getCurrency()),
            quantity,
            cost,
            normalize(sourcePosition.getCostCurrency()))
        != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "转入账户 FX Position 更新失败");
    }

    Instant movementTime = transactionTime == null ? Instant.now() : transactionTime;
    insertMovement(
        transactionId,
        0,
        "TRANSFER_OUT",
        source.getAccountId(),
        sourcePosition.getCurrency(),
        quantity.negate(),
        cost.negate(),
        sourcePosition.getCostCurrency(),
        null,
        null,
        movementTime);
    insertMovement(
        transactionId,
        1,
        "TRANSFER_IN",
        target.getAccountId(),
        sourcePosition.getCurrency(),
        quantity,
        cost,
        sourcePosition.getCostCurrency(),
        null,
        null,
        movementTime);
  }

  private void dispose(
      String transactionId,
      int order,
      String movementType,
      Account account,
      BigDecimal quantity,
      Instant transactionTime) {
    FxPosition position = positionMapper.selectByAccountIdForUpdate(account.getAccountId());
    if (position == null) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "外币账户缺少 FX Position，不能在未知成本下处置；请先建立/修复期初成本");
    }
    if (quantity.compareTo(position.getQuantity()) > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "交易金额超过已追踪 FX 持仓；请先修复账户余额与 FX Position 差异");
    }
    boolean close = quantity.compareTo(position.getQuantity()) == 0;
    BigDecimal cost =
        close
            ? position.getTotalCost()
            : quantity
                .multiply(position.getAverageCostRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    BigDecimal rate = tryEffectiveRate(transactionTime, position.getCurrency(), position.getCostCurrency());
    BigDecimal gainLoss =
        rate == null
            ? null
            : quantity
                .multiply(rate)
                .subtract(cost)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    if (positionMapper.applySellPosition(account.getAccountId(), quantity, cost, close) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Position 处置更新失败");
    }
    insertMovement(
        transactionId,
        order,
        movementType,
        account.getAccountId(),
        position.getCurrency(),
        quantity.negate(),
        cost.negate(),
        position.getCostCurrency(),
        rate,
        gainLoss,
        transactionTime == null ? Instant.now() : transactionTime);
  }

  private void acquire(
      String transactionId,
      int order,
      String movementType,
      Account account,
      BigDecimal quantity,
      Instant transactionTime) {
    FxPosition existing = positionMapper.selectByAccountId(account.getAccountId());
    String costCurrency =
        existing == null
            ? accountingPolicy.defaultCostCurrency()
            : normalize(existing.getCostCurrency());
    if (costCurrency.equals(normalize(account.getCurrency()))) return;

    BigDecimal rate = requireAcquisitionRate(transactionTime, account.getCurrency(), costCurrency);
    BigDecimal cost = quantity.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    if (positionMapper.upsertBuyPosition(
            UUID.randomUUID().toString(),
            account.getAccountId(),
            normalize(account.getCurrency()),
            quantity,
            cost,
            costCurrency)
        != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Position 流入更新失败");
    }
    insertMovement(
        transactionId,
        order,
        movementType,
        account.getAccountId(),
        account.getCurrency(),
        quantity,
        cost,
        costCurrency,
        rate,
        null,
        transactionTime == null ? Instant.now() : transactionTime);
  }

  private BigDecimal tryEffectiveRate(Instant time, String base, String quote) {
    if (normalize(base).equals(normalize(quote))) return BigDecimal.ONE;
    try {
      return exchangeRateService
          .effective(
              (time == null ? Instant.now() : time).atZone(ACCOUNTING_ZONE).toLocalDate(),
              base,
              quote,
              null)
          .exchangeRate();
    } catch (BusinessException ex) {
      return null;
    }
  }

  private BigDecimal requireAcquisitionRate(Instant time, String base, String quote) {
    if (normalize(base).equals(normalize(quote))) return BigDecimal.ONE;
    Instant effectiveTime = time == null ? Instant.now() : time;
    var rateDate = effectiveTime.atZone(ACCOUNTING_ZONE).toLocalDate();
    try {
      return exchangeRateService.effective(rateDate, base, quote, null).exchangeRate();
    } catch (BusinessException ex) {
      // TradingView provider is realtime-only. It is safe to create a snapshot automatically only
      // for today's acquisition; historical inflows must have an actual historical/manual snapshot.
      if (rateDate.equals(Instant.now().atZone(ACCOUNTING_ZONE).toLocalDate())) {
        return exchangeRateService.snapshotTradingView(rateDate, base, quote).exchangeRate();
      }
      throw new BusinessException(
          CommonErrorCode.DATA_NOT_FOUND,
          "外币流入缺少历史汇率，无法建立成本："
              + normalize(base)
              + "/"
              + normalize(quote)
              + " "
              + rateDate);
    }
  }

  private void insertMovement(
      String transactionId,
      int order,
      String type,
      String accountId,
      String currency,
      BigDecimal quantityDelta,
      BigDecimal costDelta,
      String costCurrency,
      BigDecimal valuationRate,
      BigDecimal realizedGainLoss,
      Instant now) {
    FxPositionMovement movement = new FxPositionMovement();
    movement.setMovementId(UUID.randomUUID().toString());
    movement.setTransactionId(transactionId);
    movement.setAccountId(accountId);
    movement.setMovementOrder(order);
    movement.setMovementTypeCode(type);
    movement.setCurrency(normalize(currency));
    movement.setQuantityDelta(quantityDelta);
    movement.setCostDelta(costDelta);
    movement.setCostCurrency(normalize(costCurrency));
    movement.setValuationRate(valuationRate);
    movement.setRealizedGainLoss(realizedGainLoss);
    movement.setGainLossCurrency(realizedGainLoss == null ? null : normalize(costCurrency));
    movement.setCreatedAt(now);
    movementMapper.insert(movement);
  }

  private String movementType(String transactionType, boolean inflow) {
    return switch (normalize(transactionType)) {
      case "INCOME" -> "INCOME_ACQUIRE";
      case "REFUND" -> "REFUND_ACQUIRE";
      case "EXPENSE" -> "EXPENSE_DISPOSE";
      case "CREDIT_PAYMENT" -> "CREDIT_PAYMENT_DISPOSE";
      default -> inflow ? "ASSET_ACQUIRE" : "ASSET_DISPOSE";
    };
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
