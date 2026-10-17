package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CurrencyInventoryBucket;
import com.jiajunxie.entity.FxPosition;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.FxCostInventoryService;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.FxCostBucketValuationVo;
import com.jiajunxie.vo.FxCostBucketVo;
import com.jiajunxie.vo.FxCostInventoryValuationVo;
import com.jiajunxie.vo.FxCostInventoryVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Read model for FX Domain V3 cost inventory.
 *
 * <p>Before the cutover marker exists the service exposes a compatibility projection from the
 * legacy single-cost-currency FxPosition. After cutover the only cost facts come from
 * b_currency_inventory_buckets. Account/TransactionEntry remains the balance source of truth.
 */
@Service
@RequiredArgsConstructor
public class FxCostInventoryServiceImpl implements FxCostInventoryService {

  private static final int MONEY_SCALE = 4;
  private static final int RETURN_SCALE = 10;
  private static final ZoneId ACCOUNTING_ZONE = ZoneId.of("Asia/Tokyo");
  private static final String MODE_LEGACY = "LEGACY_POSITION";
  private static final String MODE_V3 = "V3_COST_INVENTORY";

  private final AccountMapper accountMapper;
  private final FxPositionMapper fxPositionMapper;
  private final CurrencyInventoryBucketMapper bucketMapper;
  private final CurrencyInventoryRebuildMapper rebuildMapper;
  private final ExchangeRateService exchangeRateService;

  @Override
  @Transactional(readOnly = true)
  public FxCostInventoryVo get(String accountId) {
    requireAccountId(accountId);
    Account account = requireAssetAccount(accountId);
    return isCutoverActive() ? v3Inventory(account) : legacyInventory(account);
  }

  @Override
  @Transactional(readOnly = true)
  public FxCostInventoryValuationVo realtime(String accountId) {
    FxCostInventoryVo inventory = requireCostInventory(accountId);
    LocalDate valuationDate = LocalDate.now(ACCOUNTING_ZONE);
    List<FxCostBucketValuationVo> valuations = new ArrayList<>();
    for (FxCostBucketVo bucket : inventory.buckets()) {
      valuations.add(realtimeBucket(inventory.currency(), bucket));
    }
    return aggregate(inventory, "REALTIME", valuationDate, valuations);
  }

  @Override
  @Transactional(readOnly = true)
  public FxCostInventoryValuationVo reference(
      String accountId, LocalDate valuationDate, String rateSourceCode) {
    if (valuationDate == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "估值日期不能为空");
    }
    FxCostInventoryVo inventory = requireCostInventory(accountId);
    List<FxCostBucketValuationVo> valuations = new ArrayList<>();
    for (FxCostBucketVo bucket : inventory.buckets()) {
      valuations.add(referenceBucket(inventory.currency(), bucket, valuationDate, rateSourceCode));
    }
    return aggregate(inventory, "STORED", valuationDate, valuations);
  }

  public boolean isCutoverActive() {
    return rebuildMapper.selectInventoryCutoverAppliedAt() != null;
  }

  private FxCostInventoryVo v3Inventory(Account account) {
    List<CurrencyInventoryBucket> rows = bucketMapper.selectFxByAccount(account.getAccountId());
    List<FxCostBucketVo> buckets =
        rows == null
            ? List.of()
            : rows.stream()
                .filter(item -> item.getQuantity() != null && item.getQuantity().signum() > 0)
                .sorted(
                    Comparator.comparing(
                            CurrencyInventoryBucket::getSourceCurrency,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CurrencyInventoryBucket::getBucketId))
                .map(this::toBucketVo)
                .toList();
    return buildInventory(account, MODE_V3, buckets);
  }

  private FxCostInventoryVo legacyInventory(Account account) {
    FxPosition position = fxPositionMapper.selectByAccountId(account.getAccountId());
    if (position == null || position.getQuantity() == null || position.getQuantity().signum() <= 0) {
      return buildInventory(account, MODE_LEGACY, List.of());
    }
    FxCostBucketVo bucket =
        new FxCostBucketVo(
            position.getPositionId(),
            normalize(position.getCostCurrency()),
            money(position.getQuantity()),
            money(position.getTotalCost()),
            position.getAverageCostRate() == null
                ? rateFor(position.getQuantity(), position.getTotalCost())
                : position.getAverageCostRate().setScale(10, RoundingMode.HALF_UP),
            position.getUpdatedAt());
    return buildInventory(account, MODE_LEGACY, List.of(bucket));
  }

  private FxCostInventoryVo buildInventory(
      Account account, String mode, List<FxCostBucketVo> buckets) {
    BigDecimal balance = money(account.getTotalAmount());
    BigDecimal fxQuantity =
        money(
            buckets.stream()
                .map(FxCostBucketVo::quantity)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    boolean within = fxQuantity.compareTo(balance) <= 0;
    BigDecimal nonFx = within ? money(balance.subtract(fxQuantity)) : BigDecimal.ZERO.setScale(MONEY_SCALE);
    Instant updatedAt = account.getUpdatedAt();
    for (FxCostBucketVo bucket : buckets) {
      if (bucket.updatedAt() != null && (updatedAt == null || bucket.updatedAt().isAfter(updatedAt))) {
        updatedAt = bucket.updatedAt();
      }
    }
    return new FxCostInventoryVo(
        account.getAccountId(),
        normalize(account.getCurrency()),
        balance,
        fxQuantity,
        nonFx,
        within,
        mode,
        buckets,
        updatedAt);
  }

  private FxCostBucketVo toBucketVo(CurrencyInventoryBucket bucket) {
    return new FxCostBucketVo(
        bucket.getBucketId(),
        normalize(bucket.getSourceCurrency()),
        money(bucket.getQuantity()),
        money(bucket.getSourceCostAmount()),
        bucket.getAverageCostRate() == null
            ? rateFor(bucket.getQuantity(), bucket.getSourceCostAmount())
            : bucket.getAverageCostRate().setScale(10, RoundingMode.HALF_UP),
        bucket.getUpdatedAt());
  }

  private FxCostInventoryVo requireCostInventory(String accountId) {
    FxCostInventoryVo inventory = get(accountId);
    if (!inventory.fxWithinAccountBalance()) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "FX Cost 数量超过账户余额，不能估值");
    }
    if (inventory.buckets().isEmpty() || inventory.fxQuantity().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户暂无 FX Cost Inventory");
    }
    return inventory;
  }

  private FxCostBucketValuationVo realtimeBucket(String currency, FxCostBucketVo bucket) {
    try {
      RealtimeExchangeRateVo rate = exchangeRateService.realtime(currency, bucket.sourceCurrency());
      return valuedBucket(
          bucket,
          rate.exchangeRate(),
          rate.rateSourceCode(),
          null,
          rate.observedAt(),
          rate.derived());
    } catch (BusinessException ex) {
      return unavailableBucket(bucket, ex.getMessage());
    }
  }

  private FxCostBucketValuationVo referenceBucket(
      String currency,
      FxCostBucketVo bucket,
      LocalDate valuationDate,
      String rateSourceCode) {
    try {
      ExchangeRateVo rate =
          exchangeRateService.effective(
              valuationDate, currency, bucket.sourceCurrency(), rateSourceCode);
      return valuedBucket(
          bucket,
          rate.exchangeRate(),
          rate.rateSourceCode(),
          rate.rateDate(),
          null,
          rate.derived());
    } catch (BusinessException ex) {
      return unavailableBucket(bucket, ex.getMessage());
    }
  }

  private FxCostBucketValuationVo valuedBucket(
      FxCostBucketVo bucket,
      BigDecimal effectiveRate,
      String rateSourceCode,
      LocalDate rateDate,
      Instant observedAt,
      boolean derivedRate) {
    if (effectiveRate == null || effectiveRate.signum() <= 0) {
      return unavailableBucket(bucket, "估值汇率必须大于 0");
    }
    BigDecimal rate = effectiveRate.setScale(10, RoundingMode.HALF_UP);
    BigDecimal equivalentCost =
        bucket.sourceCostAmount().divide(rate, MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal gainLoss = money(bucket.quantity().subtract(equivalentCost));
    BigDecimal returnRate =
        equivalentCost.signum() == 0
            ? BigDecimal.ZERO.setScale(RETURN_SCALE, RoundingMode.HALF_UP)
            : gainLoss.divide(equivalentCost, RETURN_SCALE, RoundingMode.HALF_UP);
    return new FxCostBucketValuationVo(
        bucket.bucketId(),
        bucket.sourceCurrency(),
        bucket.quantity(),
        bucket.sourceCostAmount(),
        bucket.averageCostRate(),
        true,
        rate,
        rateSourceCode,
        rateDate,
        observedAt,
        derivedRate,
        equivalentCost,
        gainLoss,
        returnRate,
        null);
  }

  private FxCostBucketValuationVo unavailableBucket(FxCostBucketVo bucket, String message) {
    return new FxCostBucketValuationVo(
        bucket.bucketId(),
        bucket.sourceCurrency(),
        bucket.quantity(),
        bucket.sourceCostAmount(),
        bucket.averageCostRate(),
        false,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        StringUtils.hasText(message) ? message : "暂无可用汇率");
  }

  private FxCostInventoryValuationVo aggregate(
      FxCostInventoryVo inventory,
      String valuationMode,
      LocalDate valuationDate,
      List<FxCostBucketValuationVo> valuations) {
    boolean complete = valuations.stream().allMatch(FxCostBucketValuationVo::rateAvailable);
    BigDecimal equivalentCost = null;
    BigDecimal gainLoss = null;
    BigDecimal returnRate = null;
    if (complete) {
      equivalentCost =
          money(
              valuations.stream()
                  .map(FxCostBucketValuationVo::equivalentCostAccountCurrency)
                  .reduce(BigDecimal.ZERO, BigDecimal::add));
      gainLoss = money(inventory.fxQuantity().subtract(equivalentCost));
      returnRate =
          equivalentCost.signum() == 0
              ? BigDecimal.ZERO.setScale(RETURN_SCALE, RoundingMode.HALF_UP)
              : gainLoss.divide(equivalentCost, RETURN_SCALE, RoundingMode.HALF_UP);
    }
    return new FxCostInventoryValuationVo(
        inventory.accountId(),
        inventory.currency(),
        inventory.fxQuantity(),
        inventory.nonFxQuantity(),
        valuationMode,
        valuationDate,
        complete,
        equivalentCost,
        inventory.fxQuantity(),
        gainLoss,
        returnRate,
        valuations);
  }

  private Account requireAssetAccount(String accountId) {
    Account account = accountMapper.selectById(accountId);
    if (account == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户不存在");
    }
    if (!"ASSET".equals(normalize(account.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "只有资产账户具有 FX Cost Inventory");
    }
    return account;
  }

  private static void requireAccountId(String accountId) {
    if (!StringUtils.hasText(accountId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账户 ID 不能为空");
    }
  }

  private static BigDecimal money(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal rateFor(BigDecimal quantity, BigDecimal cost) {
    BigDecimal qty = money(quantity);
    if (qty.signum() == 0) return BigDecimal.ZERO.setScale(10, RoundingMode.HALF_UP);
    return money(cost).divide(qty, 10, RoundingMode.HALF_UP);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
