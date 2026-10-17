package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.CurrencyInventoryBucket;
import com.jiajunxie.entity.FxPosition;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.vo.ExchangeRateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FxCostInventoryServiceImplTest {

  private AccountMapper accountMapper;
  private FxPositionMapper positionMapper;
  private CurrencyInventoryBucketMapper bucketMapper;
  private CurrencyInventoryRebuildMapper rebuildMapper;
  private ExchangeRateService rateService;
  private FxCostInventoryServiceImpl service;

  @BeforeEach
  void setUp() {
    accountMapper = mock(AccountMapper.class);
    positionMapper = mock(FxPositionMapper.class);
    bucketMapper = mock(CurrencyInventoryBucketMapper.class);
    rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    rateService = mock(ExchangeRateService.class);
    service =
        new FxCostInventoryServiceImpl(
            accountMapper, positionMapper, bucketMapper, rebuildMapper, rateService);
  }

  @Test
  void beforeCutoverProjectsLegacyPositionAsCompatibilityInventory() {
    when(accountMapper.selectById("usd")).thenReturn(account("usd", "USD", "200"));
    when(rebuildMapper.selectInventoryCutoverAppliedAt()).thenReturn(null);
    FxPosition position = new FxPosition();
    position.setPositionId("p1");
    position.setAccountId("usd");
    position.setCurrency("USD");
    position.setQuantity(new BigDecimal("150"));
    position.setTotalCost(new BigDecimal("22500"));
    position.setCostCurrency("JPY");
    position.setAverageCostRate(new BigDecimal("150"));
    position.setUpdatedAt(Instant.parse("2026-08-18T00:00:00Z"));
    when(positionMapper.selectByAccountId("usd")).thenReturn(position);

    var result = service.get("usd");

    assertEquals("LEGACY_POSITION", result.inventoryMode());
    assertEquals(new BigDecimal("150.0000"), result.fxQuantity());
    assertEquals(new BigDecimal("50.0000"), result.nonFxQuantity());
    assertEquals(1, result.buckets().size());
    assertEquals("JPY", result.buckets().get(0).sourceCurrency());
  }

  @Test
  void afterCutoverReadsMultiSourceBucketsAndImplicitNonFxResidual() {
    when(accountMapper.selectById("usd")).thenReturn(account("usd", "USD", "250"));
    when(rebuildMapper.selectInventoryCutoverAppliedAt())
        .thenReturn(Instant.parse("2026-08-18T00:00:00Z"));
    when(bucketMapper.selectFxByAccount("usd"))
        .thenReturn(
            List.of(
                bucket("b-jpy", "usd", "USD", "JPY", "100", "10000"),
                bucket("b-cny", "usd", "USD", "CNY", "50", "400")));

    var result = service.get("usd");

    assertEquals("V3_COST_INVENTORY", result.inventoryMode());
    assertEquals(new BigDecimal("150.0000"), result.fxQuantity());
    assertEquals(new BigDecimal("100.0000"), result.nonFxQuantity());
    assertTrue(result.fxWithinAccountBalance());
    assertEquals(List.of("CNY", "JPY"), result.buckets().stream().map(b -> b.sourceCurrency()).toList());
  }

  @Test
  void referenceValuationAggregatesDifferentSourceCurrenciesInHeldCurrency() {
    when(accountMapper.selectById("usd")).thenReturn(account("usd", "USD", "250"));
    when(rebuildMapper.selectInventoryCutoverAppliedAt())
        .thenReturn(Instant.parse("2026-08-18T00:00:00Z"));
    when(bucketMapper.selectFxByAccount("usd"))
        .thenReturn(
            List.of(
                bucket("b-jpy", "usd", "USD", "JPY", "100", "10000"),
                bucket("b-cny", "usd", "USD", "CNY", "50", "400")));
    LocalDate date = LocalDate.of(2026, 8, 18);
    when(rateService.effective(date, "USD", "JPY", null)).thenReturn(rate("USD", "JPY", "125"));
    when(rateService.effective(date, "USD", "CNY", null)).thenReturn(rate("USD", "CNY", "10"));

    var result = service.reference("usd", date, null);

    assertTrue(result.valuationComplete());
    assertEquals(new BigDecimal("120.0000"), result.equivalentCostAccountCurrency());
    assertEquals(new BigDecimal("150.0000"), result.marketValueAccountCurrency());
    assertEquals(new BigDecimal("30.0000"), result.unrealizedGainLossAccountCurrency());
    assertEquals(new BigDecimal("0.2500000000"), result.unrealizedReturnRate());
    assertEquals(2, result.buckets().size());
  }

  @Test
  void missingOneRateReturnsPartialValuationWithoutInventingAggregateProfit() {
    when(accountMapper.selectById("usd")).thenReturn(account("usd", "USD", "250"));
    when(rebuildMapper.selectInventoryCutoverAppliedAt())
        .thenReturn(Instant.parse("2026-08-18T00:00:00Z"));
    when(bucketMapper.selectFxByAccount("usd"))
        .thenReturn(
            List.of(
                bucket("b-jpy", "usd", "USD", "JPY", "100", "10000"),
                bucket("b-cny", "usd", "USD", "CNY", "50", "400")));
    LocalDate date = LocalDate.of(2026, 8, 18);
    when(rateService.effective(date, "USD", "JPY", null)).thenReturn(rate("USD", "JPY", "125"));
    when(rateService.effective(date, "USD", "CNY", null))
        .thenThrow(new BusinessException(com.jiajunxie.common.exception.CommonErrorCode.DATA_NOT_FOUND, "missing"));

    var result = service.reference("usd", date, null);

    assertFalse(result.valuationComplete());
    assertEquals(null, result.equivalentCostAccountCurrency());
    assertEquals(null, result.unrealizedGainLossAccountCurrency());
    assertEquals(1, result.buckets().stream().filter(v -> !v.rateAvailable()).count());
  }

  private Account account(String id, String currency, String balance) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountName(id);
    account.setAccountClassCode("ASSET");
    account.setCurrency(currency);
    account.setTotalAmount(new BigDecimal(balance));
    account.setUpdatedAt(Instant.parse("2026-08-18T00:00:00Z"));
    return account;
  }

  private CurrencyInventoryBucket bucket(
      String id, String accountId, String currency, String source, String quantity, String cost) {
    CurrencyInventoryBucket bucket = new CurrencyInventoryBucket();
    bucket.setBucketId(id);
    bucket.setAccountId(accountId);
    bucket.setCurrency(currency);
    bucket.setOriginTypeCode("FX_EXCHANGE");
    bucket.setSourceCurrency(source);
    bucket.setQuantity(new BigDecimal(quantity));
    bucket.setSourceCostAmount(new BigDecimal(cost));
    bucket.setAverageCostRate(new BigDecimal(cost).divide(new BigDecimal(quantity), 10, java.math.RoundingMode.HALF_UP));
    bucket.setUpdatedAt(Instant.parse("2026-08-18T01:00:00Z"));
    return bucket;
  }

  private ExchangeRateVo rate(String base, String quote, String value) {
    return new ExchangeRateVo(
        "r-" + base + quote,
        LocalDate.of(2026, 8, 18),
        base,
        quote,
        new BigDecimal(value),
        "MANUAL",
        Instant.parse("2026-08-18T00:00:00Z"),
        false);
  }
}
