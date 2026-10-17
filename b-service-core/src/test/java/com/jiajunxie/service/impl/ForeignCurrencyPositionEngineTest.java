package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.FxPosition;
import com.jiajunxie.entity.FxPositionMovement;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.mapper.FxPositionMovementMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.vo.ExchangeRateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForeignCurrencyPositionEngineTest {
  @Mock AccountMapper accountMapper;
  @Mock FxPositionMapper positionMapper;
  @Mock FxPositionMovementMapper movementMapper;
  @Mock ExchangeRateService exchangeRateService;
  @Mock CurrencyInventoryLifecycleEngine currencyInventoryLifecycleEngine;

  ForeignCurrencyPositionEngine engine;

  @BeforeEach
  void setUp() {
    engine =
        new ForeignCurrencyPositionEngine(
            accountMapper,
            positionMapper,
            movementMapper,
            exchangeRateService,
            new FxAccountingPolicy("JPY"),
            currencyInventoryLifecycleEngine);
    when(currencyInventoryLifecycleEngine.isActive()).thenReturn(false);
  }

  @Test
  void delegatesEntirelyToV3LifecycleAfterCutover() {
    when(currencyInventoryLifecycleEngine.isActive()).thenReturn(true);
    List<TransactionEntry> entries = List.of(entry("usd", "10", "USD", "SINGLE"));

    engine.apply("tx-v3", "INCOME", Instant.parse("2026-08-18T10:00:00Z"), entries);

    verify(currencyInventoryLifecycleEngine)
        .applyTransaction("tx-v3", "INCOME", Instant.parse("2026-08-18T10:00:00Z"), entries);
    org.mockito.Mockito.verifyNoInteractions(positionMapper, movementMapper, exchangeRateService);
  }

  @Test
  void expenseDisposesTrackedPositionAndPersistsCostMovement() {
    Account cny = asset("cny", "CNY", "jpy");
    FxPosition position = position("cny", "CNY", "JPY", "15950", "381685", "23.9300940439");
    when(accountMapper.selectById("cny")).thenReturn(cny);
    when(positionMapper.selectByAccountId("cny")).thenReturn(position);
    when(positionMapper.selectByAccountIdForUpdate("cny")).thenReturn(position);
    when(exchangeRateService.effective(any(LocalDate.class), eq("CNY"), eq("JPY"), eq(null)))
        .thenReturn(rate("CNY", "JPY", "23.595"));
    when(positionMapper.applySellPosition(eq("cny"), eq(new BigDecimal("7920.92")), any(), eq(false)))
        .thenReturn(1);
    when(movementMapper.insert(any(FxPositionMovement.class))).thenReturn(1);

    engine.apply(
        "tx",
        "EXPENSE",
        Instant.parse("2026-08-17T10:00:00Z"),
        List.of(entry("cny", "-7920.92", "CNY", "SINGLE")));

    verify(positionMapper)
        .applySellPosition(
            eq("cny"),
            eq(new BigDecimal("7920.92")),
            eq(new BigDecimal("189548.3605")),
            eq(false));
    verify(movementMapper).insert(any(FxPositionMovement.class));
  }

  @Test
  void sameCurrencyTransferMovesExactCarryingCostBetweenAccounts() {
    Account source = asset("usd-a", "USD", null);
    Account target = asset("usd-b", "USD", null);
    FxPosition position = position("usd-a", "USD", "JPY", "1000", "140000", "140");
    when(accountMapper.selectById("usd-a")).thenReturn(source);
    when(accountMapper.selectById("usd-b")).thenReturn(target);
    when(positionMapper.selectByAccountIdForUpdate("usd-a")).thenReturn(position);
    when(positionMapper.selectByAccountId("usd-b")).thenReturn(null);
    when(positionMapper.applySellPosition("usd-a", new BigDecimal("500"), new BigDecimal("70000.0000"), false))
        .thenReturn(1);
    when(positionMapper.upsertBuyPosition(any(), eq("usd-b"), eq("USD"), eq(new BigDecimal("500")), eq(new BigDecimal("70000.0000")), eq("JPY")))
        .thenReturn(1);
    when(movementMapper.insert(any(FxPositionMovement.class))).thenReturn(1);

    engine.apply(
        "tx",
        "TRANSFER",
        Instant.parse("2026-08-17T10:00:00Z"),
        List.of(
            entry("usd-a", "-500", "USD", "SOURCE"),
            entry("usd-b", "500", "USD", "TARGET")));

    verify(positionMapper)
        .upsertBuyPosition(any(), eq("usd-b"), eq("USD"), eq(new BigDecimal("500")), eq(new BigDecimal("70000.0000")), eq("JPY"));
  }

  @Test
  void firstForeignInterestCreatesPositionUsingConfiguredAccountingCurrency() {
    Account usd = asset("usd", "USD", null);
    when(accountMapper.selectById("usd")).thenReturn(usd);
    when(positionMapper.selectByAccountId("usd")).thenReturn(null);
    when(exchangeRateService.effective(any(LocalDate.class), eq("USD"), eq("JPY"), eq(null)))
        .thenReturn(rate("USD", "JPY", "150"));
    when(positionMapper.upsertBuyPosition(
            any(), eq("usd"), eq("USD"), eq(new BigDecimal("10")), eq(new BigDecimal("1500.0000")), eq("JPY")))
        .thenReturn(1);
    when(movementMapper.insert(any(FxPositionMovement.class))).thenReturn(1);

    engine.apply(
        "interest",
        "INCOME",
        Instant.parse("2026-08-17T10:00:00Z"),
        List.of(entry("usd", "10", "USD", "SINGLE")));

    verify(positionMapper)
        .upsertBuyPosition(
            any(), eq("usd"), eq("USD"), eq(new BigDecimal("10")), eq(new BigDecimal("1500.0000")), eq("JPY"));
  }

  @Test
  void todayForeignInterestFallsBackToTradingViewSnapshotAndCreatesPosition() {
    Account usd = asset("usd", "USD", null);
    Instant now = Instant.now();
    when(accountMapper.selectById("usd")).thenReturn(usd);
    when(positionMapper.selectByAccountId("usd")).thenReturn(null);
    when(exchangeRateService.effective(any(LocalDate.class), eq("USD"), eq("JPY"), eq(null)))
        .thenThrow(
            new BusinessException(
                com.jiajunxie.common.exception.CommonErrorCode.DATA_NOT_FOUND, "missing"));
    when(exchangeRateService.snapshotTradingView(any(LocalDate.class), eq("USD"), eq("JPY")))
        .thenReturn(rate("USD", "JPY", "150"));
    when(positionMapper.upsertBuyPosition(
            any(),
            eq("usd"),
            eq("USD"),
            eq(new BigDecimal("1")),
            eq(new BigDecimal("150.0000")),
            eq("JPY")))
        .thenReturn(1);
    when(movementMapper.insert(any(FxPositionMovement.class))).thenReturn(1);

    engine.apply(
        "interest-today",
        "INCOME",
        now,
        List.of(entry("usd", "1", "USD", "SINGLE")));

    verify(exchangeRateService)
        .snapshotTradingView(any(LocalDate.class), eq("USD"), eq("JPY"));
    verify(positionMapper)
        .upsertBuyPosition(
            any(),
            eq("usd"),
            eq("USD"),
            eq(new BigDecimal("1")),
            eq(new BigDecimal("150.0000")),
            eq("JPY"));
    verify(movementMapper).insert(any(FxPositionMovement.class));
  }

  @Test
  void foreignIncomeDoesNotFallbackToOldAverageWhenHistoricalRateIsMissing() {
    Account usd = asset("usd", "USD", null);
    FxPosition position = position("usd", "USD", "JPY", "100", "14000", "140");
    when(accountMapper.selectById("usd")).thenReturn(usd);
    when(positionMapper.selectByAccountId("usd")).thenReturn(position);
    when(exchangeRateService.effective(any(LocalDate.class), eq("USD"), eq("JPY"), eq(null)))
        .thenThrow(new BusinessException(com.jiajunxie.common.exception.CommonErrorCode.DATA_NOT_FOUND, "missing"));

    assertThrows(
        BusinessException.class,
        () ->
            engine.apply(
                "income",
                "INCOME",
                Instant.parse("2026-01-20T10:00:00Z"),
                List.of(entry("usd", "1", "USD", "SINGLE"))));
  }

  @Test
  void disposalRejectsAmountBeyondTrackedPosition() {
    Account usd = asset("usd", "USD", "jpy");
    FxPosition position = position("usd", "USD", "JPY", "100", "14000", "140");
    when(accountMapper.selectById("usd")).thenReturn(usd);
    when(positionMapper.selectByAccountId("usd")).thenReturn(position);
    when(positionMapper.selectByAccountIdForUpdate("usd")).thenReturn(position);

    assertThrows(
        BusinessException.class,
        () ->
            engine.apply(
                "tx",
                "EXPENSE",
                Instant.parse("2026-08-17T10:00:00Z"),
                List.of(entry("usd", "-101", "USD", "SINGLE"))));
  }

  private Account asset(String id, String currency, String parentId) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountClassCode("ASSET");
    account.setAccountTypeCode("BANK");
    account.setCurrency(currency);
    account.setParentAccountId(parentId);
    account.setEnabled(true);
    return account;
  }

  private FxPosition position(
      String accountId, String currency, String costCurrency, String quantity, String totalCost, String average) {
    FxPosition position = new FxPosition();
    position.setAccountId(accountId);
    position.setCurrency(currency);
    position.setCostCurrency(costCurrency);
    position.setQuantity(new BigDecimal(quantity));
    position.setTotalCost(new BigDecimal(totalCost));
    position.setAverageCostRate(new BigDecimal(average));
    return position;
  }

  private TransactionEntry entry(String accountId, String amount, String currency, String role) {
    TransactionEntry entry = new TransactionEntry();
    entry.setAccountId(accountId);
    entry.setAmount(new BigDecimal(amount));
    entry.setCurrency(currency);
    entry.setEntryRole(role);
    return entry;
  }

  private ExchangeRateVo rate(String base, String quote, String value) {
    return new ExchangeRateVo(
        "r", LocalDate.of(2026, 8, 17), base, quote, new BigDecimal(value), "TRADINGVIEW", Instant.now(), false);
  }
}
