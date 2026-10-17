package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CurrencyInventoryBucket;
import com.jiajunxie.entity.CurrencyInventoryMovement;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.CurrencyInventoryMovementMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.ExchangeRateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FX Domain V3 post-cutover lifecycle engine.
 *
 * <p>The engine is dormant until the one-time V3 cutover marker exists. Before cutover the legacy
 * {@link ForeignCurrencyPositionEngine} remains authoritative. After cutover this engine maintains
 * only direct FX cost buckets; ordinary positive cash flow stays implicit in the non-FX residual.
 *
 * <p>All ordinary disposals consume each FX bucket pro-rata against the full pre-transaction
 * account balance. Same-currency transfers carry the same pro-rata FX cost into the target account.
 * A real A -> B FX exchange consumes A's current composition pro-rata and creates/merges one direct
 * B cost bucket whose source currency is A. No older ancestry is copied into the new B bucket.
 */
@Component
@RequiredArgsConstructor
public class CurrencyInventoryLifecycleEngine {
  private static final String ORIGIN_FX = "FX_EXCHANGE";
  private static final int MONEY_SCALE = 4;
  private static final int RATE_SCALE = 10;
  private static final int RATIO_SCALE = 12;
  private static final ZoneId ACCOUNTING_ZONE = ZoneId.of("Asia/Tokyo");

  private final CurrencyInventoryRebuildMapper rebuildMapper;
  private final CurrencyInventoryBucketMapper bucketMapper;
  private final CurrencyInventoryMovementMapper movementMapper;
  private final AccountMapper accountMapper;
  private final TransactionMapper transactionMapper;
  private final ExchangeRateService exchangeRateService;

  public boolean isActive() {
    long count = rebuildMapper.countInventoryCutoverRuns();
    if (count == 0) return false;
    if (count != 1) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "FX Cost Inventory cutover marker 异常：expected=1, actual=" + count);
    }
    return true;
  }

  /** Applies ordinary F4 transaction entries after Account.totalAmount has been updated. */
  @Transactional
  public void applyTransaction(
      String transactionId,
      String transactionType,
      Instant transactionTime,
      List<TransactionEntry> entries) {
    if (!isActive() || entries == null || entries.isEmpty()) return;

    String type = normalize(transactionType);
    if ("TRANSFER".equals(type)) {
      applySameCurrencyTransfer(transactionId, transactionTime, entries);
      return;
    }

    Map<String, BigDecimal> deltas = aggregateAssetDeltas(entries);
    int[] order = {0};
    for (Map.Entry<String, BigDecimal> item : deltas.entrySet()) {
      Account account = requireAssetAccount(item.getKey());
      BigDecimal delta = money(item.getValue());
      if (isPersonalAccount(account)) {
        // PERSONAL_ACCOUNT is an ASSET-shaped current account whose signed balance itself is the fact.
        // It intentionally owns no FX cost inventory and may cross zero in either direction.
        continue;
      }
      BigDecimal afterBalance = money(zeroIfNull(account.getTotalAmount()));
      BigDecimal preBalance = money(afterBalance.subtract(delta));

      if (delta.signum() < 0) {
        depleteAccount(
            transactionId,
            order,
            ordinaryMovementType(type),
            account,
            delta.abs(),
            preBalance,
            transactionTime,
            true);
      }
      // Positive ordinary cash flow intentionally creates no FX bucket. It increases only the
      // implicit non-FX residual = Account.balance - SUM(FX bucket quantity).
      assertAccountInvariant(account.getAccountId());
    }
  }

  /**
   * Applies a real cross-currency exchange after ledger entries are posted.
   *
   * <p>sourceAmount/targetAmount are the executed trade amounts before optional fees. A source
   * currency fee increases both source disposal and the target bucket's source cost. A target
   * currency fee reduces the acquired target quantity.
   */
  @Transactional
  public ExchangeAccounting applyFxExchange(
      String transactionId,
      Instant transactionTime,
      String sourceAccountId,
      String sourceCurrency,
      BigDecimal sourceAmount,
      String targetAccountId,
      String targetCurrency,
      BigDecimal targetAmount,
      BigDecimal feeAmount,
      String feeCurrency) {
    if (!isActive()) return ExchangeAccounting.empty();

    Account source = requireAssetAccount(sourceAccountId);
    Account target = requireAssetAccount(targetAccountId);
    String sourceCcy = normalize(sourceCurrency);
    String targetCcy = normalize(targetCurrency);
    if (sourceCcy.equals(targetCcy)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "FX 来源币种和目标币种必须不同");
    }
    if (!sourceCcy.equals(normalize(source.getCurrency()))
        || !targetCcy.equals(normalize(target.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Account/Currency 不一致");
    }

    BigDecimal fee = money(zeroIfNull(feeAmount));
    String feeCcy = normalizeNullable(feeCurrency);
    BigDecimal sourceOutflow = money(sourceAmount);
    BigDecimal targetQuantity = money(targetAmount);
    BigDecimal directSourceCost = money(sourceAmount);
    if (fee.signum() > 0 && sourceCcy.equals(feeCcy)) {
      sourceOutflow = money(sourceOutflow.add(fee));
      directSourceCost = sourceOutflow;
    } else if (fee.signum() > 0 && targetCcy.equals(feeCcy)) {
      targetQuantity = money(targetQuantity.subtract(fee));
    }
    if (sourceOutflow.signum() <= 0 || targetQuantity.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "FX 实际出入库数量必须大于 0");
    }

    BigDecimal sourceAfter = money(zeroIfNull(source.getTotalAmount()));
    BigDecimal sourcePre = money(sourceAfter.add(sourceOutflow));
    int[] order = {0};
    List<Consumption> consumed =
        depleteAccount(
            transactionId,
            order,
            "FX_SOURCE_DISPOSE",
            source,
            sourceOutflow,
            sourcePre,
            transactionTime,
            true);

    upsertDirectFxBucket(
        transactionId,
        order,
        target,
        sourceCcy,
        targetQuantity,
        directSourceCost);

    assertAccountInvariant(sourceAccountId);
    assertAccountInvariant(targetAccountId);
    return summarizeExchange(consumed, sourceOutflow, targetQuantity, targetCcy);
  }

  /**
   * Generic update/delete guard after cutover.
   *
   * <p>Pre-cutover ASSET transactions are frozen because their exact V3 historical cost effects are
   * represented only by the reviewed cutover snapshot. A post-cutover transaction with no movement
   * may be edited only while no later V3 movement exists on the touched account.
   */
  public void assertMutationSafe(String transactionId, List<TransactionEntry> entries) {
    if (!isActive() || entries == null || entries.isEmpty()) return;
    if (movementMapper.countByTransactionId(transactionId) > 0) return;

    Transaction transaction = transactionMapper.selectById(transactionId);
    if (transaction == null) return;
    Instant cutoverAt = rebuildMapper.selectInventoryCutoverAppliedAt();
    if (cutoverAt == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Cost Inventory cutover marker 缺少 applied_at");
    }

    List<String> assetIds = assetAccountIds(entries);
    if (assetIds.isEmpty()) return;
    Instant createdAt = transaction.getCreatedAt();
    if (createdAt == null || !createdAt.isAfter(cutoverAt)) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "该资产交易早于 FX V3 cutover，不能直接修改/删除；请使用更正/冲销交易保持成本快照可审计");
    }

    for (String accountId : assetIds) {
      if (movementMapper.countAccountMovementsAtOrAfter(accountId, createdAt) > 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "该交易之后已有 FX Cost Inventory 变动，不能直接修改/删除；请使用更正/冲销交易");
      }
    }
  }

  /** Reverses exact post-cutover bucket movements for a latest mutable transaction. */
  @Transactional
  public boolean reverse(String transactionId) {
    if (!isActive()) return false;
    List<CurrencyInventoryMovement> movements = movementMapper.selectByTransactionIdReverse(transactionId);
    if (movements == null || movements.isEmpty()) return false;

    for (CurrencyInventoryMovement movement : movements) {
      if (movementMapper.countOtherMovementsAtOrAfter(
              movement.getBucketId(), transactionId, movement.getCreatedAt())
          > 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "该 FX 成本仓已被后续交易使用，不能直接修改/删除；请使用更正/冲销交易");
      }

      CurrencyInventoryBucket bucket = bucketMapper.selectByIdForUpdate(movement.getBucketId());
      if (bucket == null) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Cost bucket 已不存在，无法反向恢复");
      }

      BigDecimal newQuantity = money(bucket.getQuantity().subtract(movement.getQuantityDelta()));
      BigDecimal sourceDelta = zeroIfNull(movement.getSourceCostDelta());
      BigDecimal newCost = money(zeroIfNull(bucket.getSourceCostAmount()).subtract(sourceDelta));
      if (newQuantity.signum() < 0 || newCost.signum() < 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "FX Cost bucket 已被占用，无法精确反向当前交易");
      }
      updateBucketAmounts(bucket, newQuantity, newCost, Instant.now());
    }
    movementMapper.deleteByTransactionId(transactionId);
    return true;
  }

  private void applySameCurrencyTransfer(
      String transactionId, Instant transactionTime, List<TransactionEntry> entries) {
    TransactionEntry sourceEntry = null;
    TransactionEntry targetEntry = null;
    for (TransactionEntry entry : entries) {
      Account account = accountMapper.selectById(entry.getAccountId());
      if (account == null || !"ASSET".equals(normalize(account.getAccountClassCode()))) continue;
      if (entry.getAmount().signum() < 0) sourceEntry = entry;
      if (entry.getAmount().signum() > 0) targetEntry = entry;
    }
    if (sourceEntry == null || targetEntry == null) return;

    Account source = requireAssetAccount(sourceEntry.getAccountId());
    Account target = requireAssetAccount(targetEntry.getAccountId());
    if (!normalize(source.getCurrency()).equals(normalize(target.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "普通转账只能迁移同币种 FX 成本");
    }

    if (isPersonalAccount(source) || isPersonalAccount(target)) {
      // Personal current accounts are deliberately outside FX inventory accounting.
      // When repaying from a normal asset account, still validate/deplete that source normally.
      if (!isPersonalAccount(source)) {
        BigDecimal outflow = money(sourceEntry.getAmount().abs());
        BigDecimal sourceAfter = money(zeroIfNull(source.getTotalAmount()));
        BigDecimal sourcePre = money(sourceAfter.add(outflow));
        depleteAccount(transactionId, new int[] {0}, "TRANSFER_OUT", source, outflow, sourcePre, transactionTime, false);
        assertAccountInvariant(source.getAccountId());
      }
      return;
    }

    BigDecimal outflow = money(sourceEntry.getAmount().abs());
    BigDecimal sourceAfter = money(zeroIfNull(source.getTotalAmount()));
    BigDecimal sourcePre = money(sourceAfter.add(outflow));
    List<CurrencyInventoryBucket> sourceBuckets = bucketMapper.selectFxByAccountForUpdate(source.getAccountId());
    validateDisposalBasis(source, sourceBuckets, outflow, sourcePre);
    if (sourceBuckets.isEmpty()) {
      assertAccountInvariant(source.getAccountId());
      assertAccountInvariant(target.getAccountId());
      return;
    }

    BigDecimal ratio = ratio(outflow, sourcePre);
    int[] order = {0};
    Instant appliedAt = Instant.now();
    for (CurrencyInventoryBucket sourceBucket : sourceBuckets) {
      BigDecimal moveQty = proportional(sourceBucket.getQuantity(), ratio, outflow.compareTo(sourcePre) == 0);
      if (moveQty.signum() == 0) continue;
      BigDecimal moveCost = proportionalCost(sourceBucket, ratio, moveQty);

      BigDecimal remainingQty = money(sourceBucket.getQuantity().subtract(moveQty));
      BigDecimal remainingCost = money(sourceBucket.getSourceCostAmount().subtract(moveCost));
      updateBucketAmounts(sourceBucket, remainingQty, remainingCost, appliedAt);
      insertMovement(
          transactionId,
          order[0]++,
          "TRANSFER_OUT",
          sourceBucket,
          moveQty.negate(),
          moveCost.negate(),
          ratio,
          null,
          null,
          null,
          appliedAt);

      CurrencyInventoryBucket targetBucket =
          requireOrCreateFxBucket(
              target, sourceBucket.getSourceCurrency(), BigDecimal.ZERO, BigDecimal.ZERO, appliedAt);
      BigDecimal targetQty = money(targetBucket.getQuantity().add(moveQty));
      BigDecimal targetCost = money(targetBucket.getSourceCostAmount().add(moveCost));
      updateBucketAmounts(targetBucket, targetQty, targetCost, appliedAt);
      insertMovement(
          transactionId,
          order[0]++,
          "TRANSFER_IN",
          targetBucket,
          moveQty,
          moveCost,
          ratio,
          null,
          null,
          null,
          appliedAt);
    }

    assertAccountInvariant(source.getAccountId());
    assertAccountInvariant(target.getAccountId());
  }

  private List<Consumption> depleteAccount(
      String transactionId,
      int[] order,
      String movementType,
      Account account,
      BigDecimal outflow,
      BigDecimal preBalance,
      Instant transactionTime,
      boolean realizedValuation) {
    List<CurrencyInventoryBucket> buckets = bucketMapper.selectFxByAccountForUpdate(account.getAccountId());
    validateDisposalBasis(account, buckets, outflow, preBalance);
    if (buckets.isEmpty()) return List.of();

    BigDecimal allocationRatio = ratio(outflow, preBalance);
    boolean fullAccountOutflow = outflow.compareTo(preBalance) == 0;
    Instant appliedAt = Instant.now();
    List<Consumption> result = new ArrayList<>();
    for (CurrencyInventoryBucket bucket : buckets) {
      BigDecimal consumedQty = proportional(bucket.getQuantity(), allocationRatio, fullAccountOutflow);
      if (consumedQty.signum() == 0) continue;
      BigDecimal consumedCost = proportionalCost(bucket, allocationRatio, consumedQty);
      BigDecimal remainingQty = money(bucket.getQuantity().subtract(consumedQty));
      BigDecimal remainingCost = money(bucket.getSourceCostAmount().subtract(consumedCost));
      updateBucketAmounts(bucket, remainingQty, remainingCost, appliedAt);

      BigDecimal valuationRate = null;
      BigDecimal equivalentCost = null;
      BigDecimal gainLoss = null;
      if (realizedValuation) {
        // ExchangeRateService uses quote-units per base-unit. For a held account currency B
        // with source-cost currency A, valuation needs A/B (e.g. JPY per USD), because
        // equivalentCost(B) = sourceCost(A) / rate(A per B). Therefore request B/A.
        valuationRate =
            tryEffectiveRate(transactionTime, account.getCurrency(), bucket.getSourceCurrency());
        if (valuationRate != null && valuationRate.signum() > 0) {
          equivalentCost = consumedCost.divide(valuationRate, MONEY_SCALE, RoundingMode.HALF_UP);
          gainLoss = money(consumedQty.subtract(equivalentCost));
        }
      }

      insertMovement(
          transactionId,
          order[0]++,
          movementType,
          bucket,
          consumedQty.negate(),
          consumedCost.negate(),
          allocationRatio,
          valuationRate,
          equivalentCost,
          gainLoss,
          appliedAt);
      result.add(new Consumption(bucket.getSourceCurrency(), consumedQty, consumedCost));
    }
    return result;
  }

  private void upsertDirectFxBucket(
      String transactionId,
      int[] order,
      Account target,
      String sourceCurrency,
      BigDecimal quantity,
      BigDecimal sourceCost) {
    Instant appliedAt = Instant.now();
    CurrencyInventoryBucket bucket =
        requireOrCreateFxBucket(target, sourceCurrency, BigDecimal.ZERO, BigDecimal.ZERO, appliedAt);
    BigDecimal newQuantity = money(bucket.getQuantity().add(quantity));
    BigDecimal newCost = money(bucket.getSourceCostAmount().add(sourceCost));
    updateBucketAmounts(bucket, newQuantity, newCost, appliedAt);
    insertMovement(
        transactionId,
        order[0]++,
        "FX_TARGET_ACQUIRE",
        bucket,
        quantity,
        sourceCost,
        null,
        null,
        null,
        null,
        appliedAt);
  }

  private CurrencyInventoryBucket requireOrCreateFxBucket(
      Account account,
      String sourceCurrency,
      BigDecimal initialQuantity,
      BigDecimal initialCost,
      Instant now) {
    String accountCurrency = normalize(account.getCurrency());
    String sourceCcy = normalize(sourceCurrency);
    if (accountCurrency.equals(sourceCcy)) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX bucket sourceCurrency 不能等于账户币种");
    }

    CurrencyInventoryBucket existing =
        bucketMapper.selectFxByAccountAndSourceForUpdate(account.getAccountId(), sourceCcy);
    if (existing != null) return existing;

    CurrencyInventoryBucket bucket = new CurrencyInventoryBucket();
    bucket.setBucketId(UUID.randomUUID().toString());
    bucket.setAccountId(account.getAccountId());
    bucket.setCurrency(accountCurrency);
    bucket.setOriginTypeCode(ORIGIN_FX);
    bucket.setSourceCurrency(sourceCcy);
    bucket.setQuantity(money(initialQuantity));
    bucket.setSourceCostAmount(money(initialCost));
    bucket.setAverageCostRate(rateFor(initialQuantity, initialCost));
    bucket.setCreatedAt(now);
    bucket.setUpdatedAt(now);
    if (bucketMapper.insert(bucket) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Cost bucket 创建失败");
    }
    return bucket;
  }

  private void updateBucketAmounts(
      CurrencyInventoryBucket bucket, BigDecimal quantity, BigDecimal sourceCost, Instant now) {
    BigDecimal qty = money(quantity);
    BigDecimal cost = money(sourceCost);
    if (qty.signum() == 0) cost = BigDecimal.ZERO.setScale(MONEY_SCALE);
    BigDecimal avg = rateFor(qty, cost);
    if (bucketMapper.updateAmounts(bucket.getBucketId(), qty, cost, avg, now) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Cost bucket 更新失败：" + bucket.getBucketId());
    }
    bucket.setQuantity(qty);
    bucket.setSourceCostAmount(cost);
    bucket.setAverageCostRate(avg);
    bucket.setUpdatedAt(now);
  }

  private void validateDisposalBasis(
      Account account,
      List<CurrencyInventoryBucket> buckets,
      BigDecimal outflow,
      BigDecimal preBalance) {
    if (preBalance.signum() < 0 || outflow.compareTo(preBalance) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "资产账户余额不足，不能完成出库：" + account.getAccountName());
    }
    BigDecimal fxQuantity = sumQuantity(buckets);
    if (fxQuantity.compareTo(preBalance) > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "FX Cost 数量超过交易前账户余额：account="
              + account.getAccountName()
              + ", fx="
              + fxQuantity
              + ", balance="
              + preBalance);
    }
  }

  private void assertAccountInvariant(String accountId) {
    Account account = requireAssetAccount(accountId);
    if (isPersonalAccount(account)) return;
    BigDecimal balance = money(zeroIfNull(account.getTotalAmount()));
    if (balance.signum() < 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "资产账户余额不能为负：" + account.getAccountName());
    }
    BigDecimal fxQuantity = sumQuantity(bucketMapper.selectFxByAccountForUpdate(accountId));
    if (fxQuantity.compareTo(balance) > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "FX Cost 数量超过账户余额：account="
              + account.getAccountName()
              + ", fx="
              + fxQuantity
              + ", balance="
              + balance);
    }
  }

  private ExchangeAccounting summarizeExchange(
      List<Consumption> consumed,
      BigDecimal sourceOutflow,
      BigDecimal targetQuantity,
      String targetCurrency) {
    if (consumed == null || consumed.isEmpty()) return ExchangeAccounting.empty();
    String sourceCostCurrency = normalize(consumed.get(0).sourceCurrency());
    if (!sourceCostCurrency.equals(normalize(targetCurrency))) return ExchangeAccounting.empty();
    for (Consumption item : consumed) {
      if (!sourceCostCurrency.equals(normalize(item.sourceCurrency()))) return ExchangeAccounting.empty();
    }
    BigDecimal fxDisposedQty =
        consumed.stream().map(Consumption::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal costBasis =
        money(consumed.stream().map(Consumption::sourceCost).reduce(BigDecimal.ZERO, BigDecimal::add));
    if (fxDisposedQty.signum() <= 0 || sourceOutflow.signum() <= 0) return ExchangeAccounting.empty();
    BigDecimal fxShare = fxDisposedQty.divide(sourceOutflow, RATIO_SCALE, RoundingMode.HALF_UP);
    BigDecimal allocatedProceeds = money(targetQuantity.multiply(fxShare));
    return new ExchangeAccounting(
        costBasis, money(allocatedProceeds.subtract(costBasis)), sourceCostCurrency);
  }

  private void insertMovement(
      String transactionId,
      int order,
      String type,
      CurrencyInventoryBucket bucket,
      BigDecimal quantityDelta,
      BigDecimal sourceCostDelta,
      BigDecimal allocationRatio,
      BigDecimal valuationRate,
      BigDecimal equivalentCost,
      BigDecimal realizedGainLoss,
      Instant createdAt) {
    if (quantityDelta == null || quantityDelta.signum() == 0) return;
    CurrencyInventoryMovement movement = new CurrencyInventoryMovement();
    movement.setMovementId(UUID.randomUUID().toString());
    movement.setTransactionId(transactionId);
    movement.setBucketId(bucket.getBucketId());
    movement.setAccountId(bucket.getAccountId());
    movement.setMovementOrder(order);
    movement.setMovementTypeCode(type);
    movement.setOriginTypeCode(ORIGIN_FX);
    movement.setCurrency(normalize(bucket.getCurrency()));
    movement.setSourceCurrency(normalize(bucket.getSourceCurrency()));
    movement.setQuantityDelta(money(quantityDelta));
    movement.setSourceCostDelta(money(sourceCostDelta));
    movement.setAllocationRatio(allocationRatio == null ? null : allocationRatio.setScale(RATIO_SCALE, RoundingMode.HALF_UP));
    movement.setValuationRate(valuationRate == null ? null : valuationRate.setScale(RATE_SCALE, RoundingMode.HALF_UP));
    movement.setEquivalentCostAccountCurrency(equivalentCost == null ? null : money(equivalentCost));
    movement.setRealizedGainLossAccountCurrency(realizedGainLoss == null ? null : money(realizedGainLoss));
    movement.setCreatedAt(createdAt);
    if (movementMapper.insert(movement) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Cost movement 写入失败");
    }
  }

  private Map<String, BigDecimal> aggregateAssetDeltas(List<TransactionEntry> entries) {
    Map<String, BigDecimal> result = new LinkedHashMap<>();
    for (TransactionEntry entry : entries) {
      Account account = accountMapper.selectById(entry.getAccountId());
      if (account == null || !"ASSET".equals(normalize(account.getAccountClassCode()))) continue;
      result.merge(entry.getAccountId(), entry.getAmount(), BigDecimal::add);
    }
    return result;
  }

  private List<String> assetAccountIds(List<TransactionEntry> entries) {
    List<String> result = new ArrayList<>();
    for (TransactionEntry entry : entries) {
      Account account = accountMapper.selectById(entry.getAccountId());
      if (account != null
          && "ASSET".equals(normalize(account.getAccountClassCode()))
          && !result.contains(account.getAccountId())) {
        result.add(account.getAccountId());
      }
    }
    return result;
  }

  private Account requireAssetAccount(String accountId) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户不存在：" + accountId);
    }
    if (!"ASSET".equals(normalize(account.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "FX Cost Inventory 仅支持资产账户");
    }
    return account;
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
      // Realized valuation is optional. Missing market data must never block the accounting fact.
      return null;
    }
  }

  private BigDecimal proportional(BigDecimal value, BigDecimal ratio, boolean consumeAll) {
    if (consumeAll) return money(value);
    return money(value.multiply(ratio));
  }

  private BigDecimal proportionalCost(
      CurrencyInventoryBucket bucket, BigDecimal ratio, BigDecimal consumedQuantity) {
    if (consumedQuantity.compareTo(bucket.getQuantity()) == 0) {
      return money(bucket.getSourceCostAmount());
    }
    return money(bucket.getSourceCostAmount().multiply(ratio));
  }

  private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    if (denominator == null || denominator.signum() <= 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Cost pro-rata 分母必须大于 0");
    }
    return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal sumQuantity(List<CurrencyInventoryBucket> buckets) {
    if (buckets == null || buckets.isEmpty()) return BigDecimal.ZERO.setScale(MONEY_SCALE);
    return money(
        buckets.stream()
            .map(CurrencyInventoryBucket::getQuantity)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  private BigDecimal rateFor(BigDecimal quantity, BigDecimal sourceCost) {
    BigDecimal qty = money(quantity);
    if (qty.signum() == 0) return BigDecimal.ZERO.setScale(RATE_SCALE);
    return money(sourceCost).divide(qty, RATE_SCALE, RoundingMode.HALF_UP);
  }

  private String ordinaryMovementType(String transactionType) {
    return switch (normalize(transactionType)) {
      case "EXPENSE" -> "EXPENSE_DISPOSE";
      case "CREDIT_PAYMENT" -> "CREDIT_PAYMENT_DISPOSE";
      default -> "ASSET_DISPOSE";
    };
  }

  private static BigDecimal money(BigDecimal value) {
    return zeroIfNull(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeNullable(String value) {
    return value == null || value.isBlank() ? null : normalize(value);
  }

  private record Consumption(String sourceCurrency, BigDecimal quantity, BigDecimal sourceCost) {}

  public record ExchangeAccounting(
      BigDecimal costBasisAmount, BigDecimal realizedGainLoss, String gainLossCurrency) {
    static ExchangeAccounting empty() {
      return new ExchangeAccounting(null, null, null);
    }
  }
  private boolean isPersonalAccount(Account account) {
    return account != null && "PERSONAL_ACCOUNT".equals(normalize(account.getAccountTypeCode()));
  }

}
