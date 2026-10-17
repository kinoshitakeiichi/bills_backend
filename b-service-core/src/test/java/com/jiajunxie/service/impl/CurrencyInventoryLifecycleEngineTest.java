package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.jiajunxie.vo.ExchangeRateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrencyInventoryLifecycleEngineTest {
  @Mock CurrencyInventoryRebuildMapper rebuildMapper;
  @Mock CurrencyInventoryBucketMapper bucketMapper;
  @Mock CurrencyInventoryMovementMapper movementMapper;
  @Mock AccountMapper accountMapper;
  @Mock TransactionMapper transactionMapper;
  @Mock ExchangeRateService exchangeRateService;

  CurrencyInventoryLifecycleEngine engine;

  @BeforeEach
  void setUp() {
    engine =
        new CurrencyInventoryLifecycleEngine(
            rebuildMapper,
            bucketMapper,
            movementMapper,
            accountMapper,
            transactionMapper,
            exchangeRateService);
  }

  @Test
  void ordinaryIncomeCreatesNoFxCostBucketAfterCutover() {
    when(rebuildMapper.countInventoryCutoverRuns()).thenReturn(1L);
    Account usd = asset("usd", "USD", "150");
    CurrencyInventoryBucket jpy = bucket("b-jpy", "usd", "USD", "JPY", "100", "14000");
    when(accountMapper.selectById("usd")).thenReturn(usd);
    when(bucketMapper.selectFxByAccountForUpdate("usd")).thenReturn(List.of(jpy));

    engine.applyTransaction(
        "income",
        "INCOME",
        Instant.parse("2026-08-18T09:00:00Z"),
        List.of(entry("usd", "50", "USD", "SINGLE")));

    verify(bucketMapper, never()).insert(any(CurrencyInventoryBucket.class));
    verify(bucketMapper, never())
        .updateAmounts(any(), any(), any(), any(), any());
    verify(movementMapper, never()).insert(any(CurrencyInventoryMovement.class));
  }

  @Test
  void ordinaryExpenseDepletesEveryFxBucketAgainstWholeAccountBalance() {
    when(rebuildMapper.countInventoryCutoverRuns()).thenReturn(1L);
    Account usd = asset("usd", "USD", "125");
    CurrencyInventoryBucket cny = bucket("b-cny", "usd", "USD", "CNY", "100", "800");
    CurrencyInventoryBucket jpy = bucket("b-jpy", "usd", "USD", "JPY", "100", "10000");
    when(accountMapper.selectById("usd")).thenReturn(usd);
    when(bucketMapper.selectFxByAccountForUpdate("usd")).thenReturn(List.of(cny, jpy));
    when(bucketMapper.updateAmounts(any(), any(), any(), any(), any())).thenReturn(1);
    when(movementMapper.insert(any(CurrencyInventoryMovement.class))).thenReturn(1);
    when(exchangeRateService.effective(any(), any(), any(), eq(null)))
        .thenThrow(new com.jiajunxie.common.exception.BusinessException(
            com.jiajunxie.common.exception.CommonErrorCode.DATA_NOT_FOUND, "missing optional rate"));

    engine.applyTransaction(
        "expense",
        "EXPENSE",
        Instant.parse("2026-08-18T09:00:00Z"),
        List.of(entry("usd", "-125", "USD", "SINGLE")));

    verify(bucketMapper)
        .updateAmounts(
            eq("b-cny"),
            eq(new BigDecimal("50.0000")),
            eq(new BigDecimal("400.0000")),
            eq(new BigDecimal("8.0000000000")),
            any());
    verify(bucketMapper)
        .updateAmounts(
            eq("b-jpy"),
            eq(new BigDecimal("50.0000")),
            eq(new BigDecimal("5000.0000")),
            eq(new BigDecimal("100.0000000000")),
            any());

    ArgumentCaptor<CurrencyInventoryMovement> movementCaptor =
        ArgumentCaptor.forClass(CurrencyInventoryMovement.class);
    verify(movementMapper, org.mockito.Mockito.times(2)).insert(movementCaptor.capture());
    List<CurrencyInventoryMovement> movements = movementCaptor.getAllValues();
    assertEquals(new BigDecimal("-50.0000"), movements.get(0).getQuantityDelta());
    assertEquals(new BigDecimal("-50.0000"), movements.get(1).getQuantityDelta());
    assertEquals(new BigDecimal("0.500000000000"), movements.get(0).getAllocationRatio());
  }


  @Test
  void ordinaryExpenseValuesConsumedSourceCostUsingHeldToSourceRate() {
    when(rebuildMapper.countInventoryCutoverRuns()).thenReturn(1L);
    Account usd = asset("usd", "USD", "125");
    CurrencyInventoryBucket jpy = bucket("b-jpy", "usd", "USD", "JPY", "100", "10000");
    when(accountMapper.selectById("usd")).thenReturn(usd);
    when(bucketMapper.selectFxByAccountForUpdate("usd")).thenReturn(List.of(jpy));
    when(bucketMapper.updateAmounts(any(), any(), any(), any(), any())).thenReturn(1);
    when(movementMapper.insert(any(CurrencyInventoryMovement.class))).thenReturn(1);
    when(exchangeRateService.effective(LocalDate.of(2026, 8, 18), "USD", "JPY", null))
        .thenReturn(
            new ExchangeRateVo(
                "r1",
                LocalDate.of(2026, 8, 18),
                "USD",
                "JPY",
                new BigDecimal("160"),
                "MANUAL",
                Instant.parse("2026-08-18T00:00:00Z"),
                false));

    engine.applyTransaction(
        "expense-valued",
        "EXPENSE",
        Instant.parse("2026-08-18T09:00:00Z"),
        List.of(entry("usd", "-125", "USD", "SINGLE")));

    verify(exchangeRateService)
        .effective(LocalDate.of(2026, 8, 18), "USD", "JPY", null);
    ArgumentCaptor<CurrencyInventoryMovement> captor =
        ArgumentCaptor.forClass(CurrencyInventoryMovement.class);
    verify(movementMapper).insert(captor.capture());
    CurrencyInventoryMovement movement = captor.getValue();
    assertEquals(new BigDecimal("160.0000000000"), movement.getValuationRate());
    assertEquals(
        new BigDecimal("31.2500"), movement.getEquivalentCostAccountCurrency());
    assertEquals(
        new BigDecimal("18.7500"), movement.getRealizedGainLossAccountCurrency());
  }

  @Test
  void preCutoverAssetTransactionIsFrozenAfterCutover() {
    when(rebuildMapper.countInventoryCutoverRuns()).thenReturn(1L);
    when(rebuildMapper.selectInventoryCutoverAppliedAt())
        .thenReturn(Instant.parse("2026-08-18T09:00:00Z"));
    when(movementMapper.countByTransactionId("old-income")).thenReturn(0L);
    when(accountMapper.selectById("usd")).thenReturn(asset("usd", "USD", "100"));
    Transaction transaction = new Transaction();
    transaction.setTransactionId("old-income");
    transaction.setCreatedAt(Instant.parse("2026-08-18T08:59:59Z"));
    when(transactionMapper.selectById("old-income")).thenReturn(transaction);

    assertThrows(
        com.jiajunxie.common.exception.BusinessException.class,
        () ->
            engine.assertMutationSafe(
                "old-income", List.of(entry("usd", "50", "USD", "SINGLE"))));
  }

  @Test
  void latestPostCutoverNonFxIncomeCanBeMutatedButLaterFxMovementFreezesIt() {
    when(rebuildMapper.countInventoryCutoverRuns()).thenReturn(1L);
    when(rebuildMapper.selectInventoryCutoverAppliedAt())
        .thenReturn(Instant.parse("2026-08-18T09:00:00Z"));
    when(movementMapper.countByTransactionId("income")).thenReturn(0L);
    when(accountMapper.selectById("usd")).thenReturn(asset("usd", "USD", "150"));
    Transaction transaction = new Transaction();
    transaction.setTransactionId("income");
    transaction.setCreatedAt(Instant.parse("2026-08-18T09:01:00Z"));
    when(transactionMapper.selectById("income")).thenReturn(transaction);
    when(movementMapper.countAccountMovementsAtOrAfter("usd", transaction.getCreatedAt()))
        .thenReturn(0L, 1L);

    assertDoesNotThrow(
        () -> engine.assertMutationSafe("income", List.of(entry("usd", "50", "USD", "SINGLE"))));
    assertThrows(
        com.jiajunxie.common.exception.BusinessException.class,
        () -> engine.assertMutationSafe("income", List.of(entry("usd", "50", "USD", "SINGLE"))));
  }

  private Account asset(String id, String currency, String balance) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountName(id);
    account.setAccountClassCode("ASSET");
    account.setAccountTypeCode("BANK");
    account.setCurrency(currency);
    account.setTotalAmount(new BigDecimal(balance));
    account.setEnabled(true);
    return account;
  }

  private CurrencyInventoryBucket bucket(
      String id,
      String accountId,
      String currency,
      String sourceCurrency,
      String quantity,
      String cost) {
    CurrencyInventoryBucket bucket = new CurrencyInventoryBucket();
    bucket.setBucketId(id);
    bucket.setAccountId(accountId);
    bucket.setCurrency(currency);
    bucket.setOriginTypeCode("FX_EXCHANGE");
    bucket.setSourceCurrency(sourceCurrency);
    bucket.setQuantity(new BigDecimal(quantity));
    bucket.setSourceCostAmount(new BigDecimal(cost));
    bucket.setAverageCostRate(
        new BigDecimal(cost).divide(new BigDecimal(quantity), 10, java.math.RoundingMode.HALF_UP));
    return bucket;
  }

  private TransactionEntry entry(String accountId, String amount, String currency, String role) {
    TransactionEntry entry = new TransactionEntry();
    entry.setAccountId(accountId);
    entry.setAmount(new BigDecimal(amount));
    entry.setCurrency(currency);
    entry.setEntryRole(role);
    return entry;
  }
}
