package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CurrencyInventoryBucket;
import com.jiajunxie.entity.FxDetail;
import com.jiajunxie.entity.FxPosition;
import com.jiajunxie.entity.FxPositionMovement;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.FxDetailMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.mapper.FxPositionMovementMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.FxExchangeService;
import com.jiajunxie.vo.FxExchangeCreatedVo;
import com.jiajunxie.vo.FxExchangeCreateVo;
import com.jiajunxie.vo.FxPositionVo;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FxExchangeServiceImpl implements FxExchangeService {

  private static final String TRANSACTION_TYPE_EXCHANGE = "EXCHANGE";
  private static final String DIRECTION_BUY_FOREIGN = "BUY_FOREIGN";
  private static final String DIRECTION_SELL_FOREIGN = "SELL_FOREIGN";
  private static final int MONEY_SCALE = 4;
  private static final ZoneId ACCOUNTING_ZONE = ZoneId.of("Asia/Tokyo");

  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper transactionEntryMapper;
  private final AccountMapper accountMapper;
  private final FxDetailMapper fxDetailMapper;
  private final FxPositionMapper fxPositionMapper;
  private final ExchangeRateService exchangeRateService;
  private final FxPositionMovementMapper fxPositionMovementMapper;
  private final CurrencyInventoryLifecycleEngine currencyInventoryLifecycleEngine;
  private final CurrencyInventoryBucketMapper currencyInventoryBucketMapper;

  /**
   * Posts BUY_FOREIGN / SELL_FOREIGN ledger facts and FxDetail atomically. Before V3 cutover it
   * maintains legacy FxPosition; after cutover it writes direct source-currency FX Cost Inventory
   * buckets instead. Client-side preview cost values are never accepted as accounting facts.
   */
  @Override
  @Transactional
  public FxExchangeCreatedVo create(FxExchangeCreateVo command) {
    validateShape(command);
    FxExchangeCreateVo normalized = withDerivedExecutedRate(command);
    lockExchangeAccounts(normalized);
    if (currencyInventoryLifecycleEngine.isActive()) {
      return createV3(normalized);
    }
    String direction = normalize(normalized.fxDirectionCode());
    return DIRECTION_BUY_FOREIGN.equals(direction) ? createBuy(normalized) : createSell(normalized);
  }

  /**
   * Post-cutover FX path. Direct V3 cost is always kept in the actual source currency and never
   * normalized through a third/base currency. Legacy FxPosition is deliberately not mutated.
   */
  private FxExchangeCreatedVo createV3(FxExchangeCreateVo command) {
    Account sourceAccount = requireAccount(command.sourceAccountId(), "来源账户不存在");
    Account targetAccount = requireAccount(command.targetAccountId(), "目标账户不存在");
    validateAccounts(command, sourceAccount, targetAccount);

    String sourceCurrency = normalize(command.sourceCurrency());
    String targetCurrency = normalize(command.targetCurrency());
    BigDecimal feeAmount = zeroIfNull(command.feeAmount());
    String feeCurrency = normalizeNullable(command.feeCurrency());
    String transactionId = UUID.randomUUID().toString();
    Instant transactionTime = command.transactionTime() == null ? Instant.now() : command.transactionTime();
    Instant now = Instant.now();

    insertTransaction(transactionId, command, transactionTime, now);
    postEntry(transactionId, sourceAccount.getAccountId(), command.sourceAmount().negate(), sourceCurrency, "SOURCE", now);
    postEntry(transactionId, targetAccount.getAccountId(), command.targetAmount(), targetCurrency, "TARGET", now);
    postFeeEntry(transactionId, sourceAccount, targetAccount, feeAmount, feeCurrency, sourceCurrency, targetCurrency, now);

    CurrencyInventoryLifecycleEngine.ExchangeAccounting accounting =
        currencyInventoryLifecycleEngine.applyFxExchange(
            transactionId,
            transactionTime,
            sourceAccount.getAccountId(),
            sourceCurrency,
            command.sourceAmount(),
            targetAccount.getAccountId(),
            targetCurrency,
            command.targetAmount(),
            feeAmount,
            feeCurrency);

    FxDetail detail =
        buildDetail(
            transactionId,
            command,
            normalize(command.fxDirectionCode()),
            sourceAccount,
            targetAccount,
            feeAmount,
            feeCurrency,
            now);
    detail.setCostBasisAmount(accounting.costBasisAmount());
    detail.setRealizedGainLoss(accounting.realizedGainLoss());
    detail.setGainLossCurrency(accounting.gainLossCurrency());
    fxDetailMapper.insert(detail);
    persistExecutedRateSnapshot(transactionTime, sourceCurrency, targetCurrency, command.executedRate());

    return new FxExchangeCreatedVo(
        transactionId,
        normalize(command.fxDirectionCode()),
        accounting.costBasisAmount(),
        accounting.realizedGainLoss(),
        accounting.gainLossCurrency());
  }

  /**
   * Locks both account rows in stable ID order before deciding legacy vs V3. This closes the cutover
   * race where an FX request could observe "no marker", block behind cutover, then continue on the
   * legacy path after the marker had committed.
   */
  private void lockExchangeAccounts(FxExchangeCreateVo command) {
    String first = command.sourceAccountId().compareTo(command.targetAccountId()) <= 0
        ? command.sourceAccountId()
        : command.targetAccountId();
    String second = first.equals(command.sourceAccountId())
        ? command.targetAccountId()
        : command.sourceAccountId();
    accountMapper.lockById(first);
    if (!second.equals(first)) accountMapper.lockById(second);
  }

  private FxExchangeCreateVo withDerivedExecutedRate(FxExchangeCreateVo command) {
    BigDecimal derivedRate =
        command.targetAmount().divide(command.sourceAmount(), 10, RoundingMode.HALF_UP);
    return new FxExchangeCreateVo(
        command.fxDirectionCode(),
        command.sourceAccountId(),
        command.targetAccountId(),
        command.sourceAmount(),
        command.sourceCurrency(),
        command.targetAmount(),
        command.targetCurrency(),
        derivedRate,
        command.feeAmount(),
        command.feeCurrency(),
        command.remark(),
        command.transactionTime());
  }

  private FxExchangeCreatedVo createBuy(FxExchangeCreateVo command) {
    Account sourceAccount = requireAccount(command.sourceAccountId(), "来源账户不存在");
    Account targetAccount = requireAccount(command.targetAccountId(), "目标账户不存在");
    validateAccounts(command, sourceAccount, targetAccount);

    FxPosition sourcePosition = fxPositionMapper.selectByAccountIdForUpdate(sourceAccount.getAccountId());
    FxPosition targetPosition = fxPositionMapper.selectByAccountIdForUpdate(targetAccount.getAccountId());

    String sourceCurrency = normalize(command.sourceCurrency());
    String targetCurrency = normalize(command.targetCurrency());
    BigDecimal feeAmount = zeroIfNull(command.feeAmount());
    String feeCurrency = normalizeNullable(command.feeCurrency());
    String transactionId = UUID.randomUUID().toString();
    Instant transactionTime = command.transactionTime() == null ? Instant.now() : command.transactionTime();
    Instant now = Instant.now();

    insertTransaction(transactionId, command, transactionTime, now);
    postEntry(transactionId, sourceAccount.getAccountId(), command.sourceAmount().negate(), sourceCurrency, "SOURCE", now);
    postEntry(transactionId, targetAccount.getAccountId(), command.targetAmount(), targetCurrency, "TARGET", now);
    postFeeEntry(transactionId, sourceAccount, targetAccount, feeAmount, feeCurrency, sourceCurrency, targetCurrency, now);

    FxDetail detail = buildDetail(transactionId, command, DIRECTION_BUY_FOREIGN, sourceAccount, targetAccount, feeAmount, feeCurrency, now);
    BuyAccounting accounting =
        applyBuyAccounting(
            sourceAccount,
            targetAccount,
            sourcePosition,
            targetPosition,
            sourceCurrency,
            targetCurrency,
            command.sourceAmount(),
            command.targetAmount(),
            feeAmount,
            feeCurrency,
            transactionTime,
            transactionId);

    if (accounting.sourceCostBasis() != null) {
      detail.setCostBasisAmount(accounting.sourceCostBasis());
      detail.setRealizedGainLoss(accounting.sourceRealizedGainLoss());
      detail.setGainLossCurrency(accounting.costCurrency());
    }
    fxDetailMapper.insert(detail);
    persistExecutedRateSnapshot(transactionTime, sourceCurrency, targetCurrency, command.executedRate());

    insertPositionMovement(
        transactionId,
        accounting.targetMovementOrder(),
        "FX_BUY",
        targetAccount.getAccountId(),
        targetCurrency,
        accounting.targetQuantity(),
        accounting.targetCost(),
        accounting.costCurrency(),
        accounting.targetCost()
            .divide(accounting.targetQuantity(), 10, RoundingMode.HALF_UP),
        null,
        transactionTime);
    return new FxExchangeCreatedVo(
        transactionId,
        DIRECTION_BUY_FOREIGN,
        accounting.sourceCostBasis(),
        accounting.sourceRealizedGainLoss(),
        accounting.sourceCostBasis() == null ? null : accounting.costCurrency());
  }

  private FxExchangeCreatedVo createSell(FxExchangeCreateVo command) {
    Account sourceAccount = requireAccount(command.sourceAccountId(), "来源账户不存在");
    Account targetAccount = requireAccount(command.targetAccountId(), "目标账户不存在");
    validateAccounts(command, sourceAccount, targetAccount);

    String sourceCurrency = normalize(command.sourceCurrency());
    String targetCurrency = normalize(command.targetCurrency());
    BigDecimal feeAmount = zeroIfNull(command.feeAmount());
    String feeCurrency = normalizeNullable(command.feeCurrency());
    BigDecimal disposedQuantity = command.sourceAmount();
    if (feeAmount.signum() > 0 && sourceCurrency.equals(feeCurrency)) {
      disposedQuantity = disposedQuantity.add(feeAmount);
    }

    FxPosition position = fxPositionMapper.selectByAccountIdForUpdate(sourceAccount.getAccountId());
    if (position == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "来源账户没有可卖出的 FX Position");
    }
    if (!sourceCurrency.equals(normalize(position.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Position 币种与来源币种不一致");
    }
    if (!targetCurrency.equals(normalize(position.getCostCurrency()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "SELL_FOREIGN 第一版仅支持卖回持仓成本币种");
    }
    if (disposedQuantity.compareTo(position.getQuantity()) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "卖出数量（含来源币种手续费）超过当前持仓");
    }

    boolean closePosition = disposedQuantity.compareTo(position.getQuantity()) == 0;
    BigDecimal costBasis = closePosition
        ? position.getTotalCost()
        : disposedQuantity.multiply(position.getAverageCostRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal netProceeds = command.targetAmount();
    if (feeAmount.signum() > 0 && targetCurrency.equals(feeCurrency)) {
      netProceeds = netProceeds.subtract(feeAmount);
    }
    BigDecimal realizedGainLoss = netProceeds.subtract(costBasis).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    String transactionId = UUID.randomUUID().toString();
    Instant transactionTime = command.transactionTime() == null ? Instant.now() : command.transactionTime();
    Instant now = Instant.now();
    insertTransaction(transactionId, command, transactionTime, now);
    postEntry(transactionId, sourceAccount.getAccountId(), command.sourceAmount().negate(), sourceCurrency, "SOURCE", now);
    postEntry(transactionId, targetAccount.getAccountId(), command.targetAmount(), targetCurrency, "TARGET", now);
    postFeeEntry(transactionId, sourceAccount, targetAccount, feeAmount, feeCurrency, sourceCurrency, targetCurrency, now);

    FxDetail detail = buildDetail(transactionId, command, DIRECTION_SELL_FOREIGN, sourceAccount, targetAccount, feeAmount, feeCurrency, now);
    detail.setCostBasisAmount(costBasis);
    detail.setRealizedGainLoss(realizedGainLoss);
    detail.setGainLossCurrency(targetCurrency);
    fxDetailMapper.insert(detail);
    persistExecutedRateSnapshot(transactionTime, sourceCurrency, targetCurrency, command.executedRate());

    int affected = fxPositionMapper.applySellPosition(sourceAccount.getAccountId(), disposedQuantity, costBasis, closePosition);
    if (affected != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Position 卖出更新失败");
    }
    insertPositionMovement(
        transactionId,
        0,
        "FX_SELL",
        sourceAccount.getAccountId(),
        sourceCurrency,
        disposedQuantity.negate(),
        costBasis.negate(),
        targetCurrency,
        netProceeds.divide(disposedQuantity, 10, RoundingMode.HALF_UP),
        realizedGainLoss,
        transactionTime);
    return new FxExchangeCreatedVo(transactionId, DIRECTION_SELL_FOREIGN, costBasis, realizedGainLoss, targetCurrency);
  }

  private void insertTransaction(String transactionId, FxExchangeCreateVo command, Instant transactionTime, Instant now) {
    Transaction transaction = new Transaction();
    transaction.setTransactionId(transactionId);
    transaction.setTransactionType(TRANSACTION_TYPE_EXCHANGE);
    transaction.setRemark(trimToNull(command.remark()));
    transaction.setTransactionTime(transactionTime);
    transaction.setCreatedAt(now);
    transaction.setUpdatedAt(now);
    transactionMapper.insert(transaction);
  }

  private FxDetail buildDetail(String transactionId, FxExchangeCreateVo command, String direction, Account sourceAccount, Account targetAccount, BigDecimal feeAmount, String feeCurrency, Instant now) {
    FxDetail detail = new FxDetail();
    detail.setTransactionId(transactionId);
    detail.setFxDirectionCode(direction);
    detail.setSourceAccountId(sourceAccount.getAccountId());
    detail.setTargetAccountId(targetAccount.getAccountId());
    detail.setSourceAmount(command.sourceAmount());
    detail.setSourceCurrency(normalize(command.sourceCurrency()));
    detail.setTargetAmount(command.targetAmount());
    detail.setTargetCurrency(normalize(command.targetCurrency()));
    detail.setExecutedRate(command.executedRate());
    detail.setFeeAmount(feeAmount.signum() == 0 ? null : feeAmount);
    detail.setFeeCurrency(feeAmount.signum() == 0 ? null : feeCurrency);
    detail.setCostBasisAmount(null);
    detail.setRealizedGainLoss(null);
    detail.setGainLossCurrency(null);
    detail.setCreatedAt(now);
    return detail;
  }

  private void postFeeEntry(String transactionId, Account sourceAccount, Account targetAccount, BigDecimal feeAmount, String feeCurrency, String sourceCurrency, String targetCurrency, Instant now) {
    if (feeAmount.signum() <= 0) return;
    if (sourceCurrency.equals(feeCurrency)) {
      postEntry(transactionId, sourceAccount.getAccountId(), feeAmount.negate(), sourceCurrency, "FEE", now);
    } else {
      postEntry(transactionId, targetAccount.getAccountId(), feeAmount.negate(), targetCurrency, "FEE", now);
    }
  }

  /**
   * Applies moving weighted average cost for BUY_FOREIGN, including cross-FX acquisitions.
   *
   * <p>A target position has one sticky cost currency. If the target already has a position, every
   * later acquisition is converted into that same cost currency before being added to the weighted
   * average. If the target is new and the source itself is a tracked FX position, the target inherits
   * the source position cost currency; otherwise the source account currency becomes the initial cost
   * currency.
   *
   * <p>When the source is a tracked foreign-currency position, BUY_FOREIGN is also a disposal of that
   * source position. Its carrying cost is removed and realized FX gain/loss is recorded in the shared
   * cost currency. This makes CNY -> USD followed by JPY -> USD safe and keeps the USD position in one
   * comparable cost basis.
   */
  private BuyAccounting applyBuyAccounting(
      Account sourceAccount,
      Account targetAccount,
      FxPosition sourcePosition,
      FxPosition targetPosition,
      String sourceCurrency,
      String targetCurrency,
      BigDecimal sourceAmount,
      BigDecimal targetAmount,
      BigDecimal feeAmount,
      String feeCurrency,
      Instant transactionTime,
      String transactionId) {
    BigDecimal targetQuantity = targetAmount;
    BigDecimal sourceOutflow = sourceAmount;
    if (feeAmount.signum() > 0) {
      if (sourceCurrency.equals(feeCurrency)) {
        sourceOutflow = sourceOutflow.add(feeAmount);
      } else if (targetCurrency.equals(feeCurrency)) {
        targetQuantity = targetQuantity.subtract(feeAmount);
      }
    }
    if (targetQuantity.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "FX 实际入账外币数量必须大于 0");
    }

    String costCurrency =
        targetPosition != null
            ? normalize(targetPosition.getCostCurrency())
            : sourcePosition != null && sourcePosition.getQuantity() != null && sourcePosition.getQuantity().signum() > 0
                ? normalize(sourcePosition.getCostCurrency())
                : sourceCurrency;

    BigDecimal sourceToCostRate =
        sourceCurrency.equals(costCurrency)
            ? BigDecimal.ONE
            : requireEffectiveRate(transactionTime, sourceCurrency, costCurrency);
    BigDecimal targetCost =
        sourceOutflow.multiply(sourceToCostRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    BigDecimal sourceCostBasis = null;
    BigDecimal sourceRealizedGainLoss = null;
    int targetMovementOrder = 0;
    if (sourcePosition != null
        && sourcePosition.getQuantity() != null
        && sourcePosition.getQuantity().signum() > 0) {
      if (!sourceCurrency.equals(normalize(sourcePosition.getCurrency()))) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "来源 FX Position 币种与来源账户币种不一致");
      }
      if (!costCurrency.equals(normalize(sourcePosition.getCostCurrency()))) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "来源与目标持仓成本币种不一致，当前交易无法形成统一移动平均成本");
      }
      if (sourceOutflow.compareTo(sourcePosition.getQuantity()) > 0) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "来源金额（含手续费）超过来源 FX 持仓");
      }

      boolean closeSource = sourceOutflow.compareTo(sourcePosition.getQuantity()) == 0;
      sourceCostBasis =
          closeSource
              ? sourcePosition.getTotalCost()
              : sourceOutflow
                  .multiply(sourcePosition.getAverageCostRate())
                  .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      sourceRealizedGainLoss =
          targetCost.subtract(sourceCostBasis).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      if (fxPositionMapper.applySellPosition(
              sourceAccount.getAccountId(), sourceOutflow, sourceCostBasis, closeSource)
          != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "来源 FX Position 处置失败");
      }
      insertPositionMovement(
          transactionId,
          0,
          "CROSS_FX_SOURCE_DISPOSE",
          sourceAccount.getAccountId(),
          sourceCurrency,
          sourceOutflow.negate(),
          sourceCostBasis.negate(),
          costCurrency,
          sourceToCostRate,
          sourceRealizedGainLoss,
          transactionTime);
      targetMovementOrder = 1;
    }

    int affected =
        fxPositionMapper.upsertBuyPosition(
            UUID.randomUUID().toString(),
            targetAccount.getAccountId(),
            targetCurrency,
            targetQuantity,
            targetCost,
            costCurrency);
    if (affected != 1) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "FX Position 更新失败；目标持仓无法统一到既有成本币种");
    }

    return new BuyAccounting(
        targetQuantity,
        targetCost,
        costCurrency,
        sourceCostBasis,
        sourceRealizedGainLoss,
        targetMovementOrder);
  }

  private BigDecimal requireEffectiveRate(
      Instant transactionTime, String baseCurrency, String quoteCurrency) {
    LocalDate rateDate =
        (transactionTime == null ? Instant.now() : transactionTime)
            .atZone(ACCOUNTING_ZONE)
            .toLocalDate();
    try {
      return exchangeRateService
          .effective(rateDate, baseCurrency, quoteCurrency, null)
          .exchangeRate();
    } catch (BusinessException ex) {
      if (rateDate.equals(LocalDate.now(ACCOUNTING_ZONE))) {
        return exchangeRateService
            .snapshotTradingView(rateDate, baseCurrency, quoteCurrency)
            .exchangeRate();
      }
      throw new BusinessException(
          CommonErrorCode.DATA_NOT_FOUND,
          "缺少跨币种成本换算汇率："
              + normalize(baseCurrency)
              + "/"
              + normalize(quoteCurrency)
              + " "
              + rateDate);
    }
  }

  private record BuyAccounting(
      BigDecimal targetQuantity,
      BigDecimal targetCost,
      String costCurrency,
      BigDecimal sourceCostBasis,
      BigDecimal sourceRealizedGainLoss,
      int targetMovementOrder) {}

  @Override
  public FxPositionVo getPosition(String accountId) {
    requireText(accountId, "账户 ID 不能为空");
    Account account = requireAccount(accountId, "账户不存在");
    if (!"ASSET".equals(normalizeNullable(account.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "只有资产账户具有 FX Position");
    }

    if (currencyInventoryLifecycleEngine.isActive()) {
      List<CurrencyInventoryBucket> buckets =
          currencyInventoryBucketMapper.selectFxByAccount(accountId);
      if (buckets == null || buckets.isEmpty()) {
        return null;
      }
      if (buckets.size() != 1) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "该账户已有多来源 FX Cost Inventory；Legacy FX Position API 无法表示，请使用 /api/v1/fx/inventory/"
                + accountId);
      }
      CurrencyInventoryBucket bucket = buckets.get(0);
      return new FxPositionVo(
          bucket.getBucketId(),
          bucket.getAccountId(),
          bucket.getCurrency(),
          bucket.getQuantity(),
          bucket.getSourceCostAmount(),
          bucket.getSourceCurrency(),
          bucket.getAverageCostRate(),
          bucket.getUpdatedAt());
    }

    FxPosition position = fxPositionMapper.selectByAccountId(accountId);
    if (position == null) {
      return null;
    }

    return new FxPositionVo(
        position.getPositionId(),
        position.getAccountId(),
        position.getCurrency(),
        position.getQuantity(),
        position.getTotalCost(),
        position.getCostCurrency(),
        position.getAverageCostRate(),
        position.getUpdatedAt());
  }

  private void postEntry(
      String transactionId,
      String accountId,
      BigDecimal amount,
      String currency,
      String entryRole,
      Instant now) {
    TransactionEntry entry = new TransactionEntry();
    entry.setEntryId(UUID.randomUUID().toString());
    entry.setTransactionId(transactionId);
    entry.setAccountId(accountId);
    entry.setAmount(amount);
    entry.setCurrency(currency);
    entry.setEntryRole(entryRole);
    entry.setCreatedAt(now);
    transactionEntryMapper.insert(entry);

    int affected = accountMapper.addBalance(accountId, amount);
    if (affected != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX 账户余额更新失败");
    }
  }

  private void validateShape(FxExchangeCreateVo command) {
    if (command == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "FX 请求不能为空");
    }
    String direction = normalizeNullable(command.fxDirectionCode());
    if (!DIRECTION_BUY_FOREIGN.equals(direction) && !DIRECTION_SELL_FOREIGN.equals(direction)) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "FX 方向仅支持 BUY_FOREIGN 或 SELL_FOREIGN");
    }
    requireText(command.sourceAccountId(), "来源账户不能为空");
    requireText(command.targetAccountId(), "目标账户不能为空");
    requirePositive(command.sourceAmount(), "来源金额必须大于 0");
    requirePositive(command.targetAmount(), "目标金额必须大于 0");

    String sourceCurrency = normalizeNullable(command.sourceCurrency());
    String targetCurrency = normalizeNullable(command.targetCurrency());
    if (sourceCurrency == null || targetCurrency == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "来源币种和目标币种不能为空");
    }
    if (sourceCurrency.equals(targetCurrency)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "FX 来源币种和目标币种必须不同");
    }

    BigDecimal feeAmount = zeroIfNull(command.feeAmount());
    if (feeAmount.signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "手续费不能小于 0");
    }
    String feeCurrency = normalizeNullable(command.feeCurrency());
    if (feeAmount.signum() > 0) {
      if (feeCurrency == null) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "存在手续费时手续费币种不能为空");
      }
      if (!sourceCurrency.equals(feeCurrency) && !targetCurrency.equals(feeCurrency)) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "第一版暂不支持第三币种手续费");
      }
      if (targetCurrency.equals(feeCurrency)
          && feeAmount.compareTo(command.targetAmount()) >= 0) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "目标币种手续费必须小于目标金额");
      }
    }
  }

  private void validateAccounts(
      FxExchangeCreateVo command, Account sourceAccount, Account targetAccount) {
    if (sourceAccount.getAccountId().equals(targetAccount.getAccountId())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "FX 来源账户和目标账户必须不同");
    }
    if (!Boolean.TRUE.equals(sourceAccount.getEnabled())
        || !Boolean.TRUE.equals(targetAccount.getEnabled())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX 账户必须处于启用状态");
    }
    if (!"ASSET".equals(normalizeNullable(sourceAccount.getAccountClassCode()))
        || !"ASSET".equals(normalizeNullable(targetAccount.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX 第一版仅支持资产账户之间兑换");
    }
    if (!normalize(sourceAccount.getCurrency()).equals(normalize(command.sourceCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "来源账户币种与来源币种不一致");
    }
    if (!normalize(targetAccount.getCurrency()).equals(normalize(command.targetCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "目标账户币种与目标币种不一致");
    }

    // parentAccountId is organizational only and does not constrain FX eligibility/cost currency.

    BigDecimal sourceDebit = command.sourceAmount();
    BigDecimal feeAmount = zeroIfNull(command.feeAmount());
    String feeCurrency = normalizeNullable(command.feeCurrency());
    if (feeAmount.signum() > 0
        && normalize(command.sourceCurrency()).equals(feeCurrency)) {
      sourceDebit = sourceDebit.add(feeAmount);
    }

    BigDecimal available =
        sourceAccount.getTotalAmount() == null ? BigDecimal.ZERO : sourceAccount.getTotalAmount();
    if (available.compareTo(sourceDebit) < 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "来源账户余额不足");
    }
  }

  private void insertPositionMovement(
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
    movement.setCurrency(currency);
    movement.setQuantityDelta(quantityDelta);
    movement.setCostDelta(costDelta);
    movement.setCostCurrency(costCurrency);
    movement.setValuationRate(valuationRate);
    movement.setRealizedGainLoss(realizedGainLoss);
    movement.setGainLossCurrency(realizedGainLoss == null ? null : costCurrency);
    movement.setCreatedAt(now);
    fxPositionMovementMapper.insert(movement);
  }

  private void persistExecutedRateSnapshot(
      Instant transactionTime, String sourceCurrency, String targetCurrency, BigDecimal executedRate) {
    LocalDate rateDate = transactionTime.atZone(ACCOUNTING_ZONE).toLocalDate();
    BigDecimal dailyWeightedRate =
        fxDetailMapper.selectDailyWeightedExecutedRate(rateDate, sourceCurrency, targetCurrency);
    exchangeRateService.upsertFxExecuted(
        rateDate,
        sourceCurrency,
        targetCurrency,
        dailyWeightedRate == null ? executedRate : dailyWeightedRate);
  }

  private Account requireAccount(String accountId, String message) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, message);
    }
    return account;
  }

  private static void requirePositive(BigDecimal value, String message) {
    if (value == null || value.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
    }
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
    }
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeNullable(String value) {
    return value == null || value.isBlank() ? null : normalize(value);
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
