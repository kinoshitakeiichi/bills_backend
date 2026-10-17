package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.FxDetail;
import com.jiajunxie.entity.FxPosition;
import com.jiajunxie.entity.FxPositionMovement;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.FxDetailMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.vo.FxExchangeCreateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FxExchangeServiceImplTest {

  @Test
  void buyForeignCreatesExchangeEntriesAndFxDetail() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    allowPositionUpsert(f);

    var result =
        f.service.create(
            command(
                new BigDecimal("15000"),
                new BigDecimal("100"),
                new BigDecimal("0.0066666667"),
                null,
                null));

    assertNotNull(result.transactionId());
    assertEquals("BUY_FOREIGN", result.fxDirectionCode());

    verify(f.transactionMapper).insert(any(Transaction.class));
    verify(f.transactionEntryMapper, Mockito.times(2)).insert(any(TransactionEntry.class));
    verify(f.accountMapper).addBalance("jpy", new BigDecimal("-15000"));
    verify(f.accountMapper).addBalance("usd", new BigDecimal("100"));

    ArgumentCaptor<FxDetail> detailCaptor = ArgumentCaptor.forClass(FxDetail.class);
    verify(f.fxDetailMapper).insert(detailCaptor.capture());
    FxDetail detail = detailCaptor.getValue();
    assertEquals("BUY_FOREIGN", detail.getFxDirectionCode());
    assertEquals("jpy", detail.getSourceAccountId());
    assertEquals("usd", detail.getTargetAccountId());
    assertEquals(new BigDecimal("15000"), detail.getSourceAmount());
    assertEquals(new BigDecimal("100"), detail.getTargetAmount());
  }

  @Test
  void sourceCurrencyFeeCreatesSeparateNegativeFeeEntry() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-300"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    allowPositionUpsert(f);

    f.service.create(
        command(
            new BigDecimal("15000"),
            new BigDecimal("100"),
            new BigDecimal("0.0066666667"),
            new BigDecimal("300"),
            "JPY"));

    verify(f.transactionEntryMapper, Mockito.times(3)).insert(any(TransactionEntry.class));
    verify(f.accountMapper).addBalance("jpy", new BigDecimal("-300"));
  }

  @Test
  void targetCurrencyFeeReducesTargetAccountWithFeeEntry() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    allowPositionUpsert(f);
    when(f.accountMapper.addBalance("usd", new BigDecimal("-1"))).thenReturn(1);

    f.service.create(
        command(
            new BigDecimal("15000"),
            new BigDecimal("100"),
            new BigDecimal("0.0066666667"),
            BigDecimal.ONE,
            "USD"));

    verify(f.accountMapper).addBalance("usd", new BigDecimal("-1"));
  }

  @Test
  void buyPositionUsesTargetAmountAndSourceAmountWithoutFee() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    allowPositionUpsert(f);

    f.service.create(
        command(
            new BigDecimal("15000"),
            new BigDecimal("100"),
            new BigDecimal("0.0066666667"),
            null,
            null));

    verify(f.fxPositionMapper)
        .upsertBuyPosition(
            any(String.class),
            org.mockito.ArgumentMatchers.eq("usd"),
            org.mockito.ArgumentMatchers.eq("USD"),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("100")),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("15000.0000")),
            org.mockito.ArgumentMatchers.eq("JPY"));
  }

  @Test
  void sourceCurrencyFeeIncreasesPositionCost() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-300"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    allowPositionUpsert(f);

    f.service.create(
        command(
            new BigDecimal("15000"),
            new BigDecimal("100"),
            new BigDecimal("0.0066666667"),
            new BigDecimal("300"),
            "JPY"));

    verify(f.fxPositionMapper)
        .upsertBuyPosition(
            any(String.class),
            org.mockito.ArgumentMatchers.eq("usd"),
            org.mockito.ArgumentMatchers.eq("USD"),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("100")),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("15300.0000")),
            org.mockito.ArgumentMatchers.eq("JPY"));
  }

  @Test
  void targetCurrencyFeeReducesPositionQuantity() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("-1"))).thenReturn(1);
    allowPositionUpsert(f);

    f.service.create(
        command(
            new BigDecimal("15000"),
            new BigDecimal("100"),
            new BigDecimal("0.0066666667"),
            BigDecimal.ONE,
            "USD"));

    verify(f.fxPositionMapper)
        .upsertBuyPosition(
            any(String.class),
            org.mockito.ArgumentMatchers.eq("usd"),
            org.mockito.ArgumentMatchers.eq("USD"),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("99")),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("15000.0000")),
            org.mockito.ArgumentMatchers.eq("JPY"));
  }

  @Test
  void positionCostCurrencyConflictRejectsWholeOperation() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    when(f.fxPositionMapper.upsertBuyPosition(
            any(String.class),
            org.mockito.ArgumentMatchers.eq("usd"),
            org.mockito.ArgumentMatchers.eq("USD"),
            org.mockito.ArgumentMatchers.any(BigDecimal.class),
            org.mockito.ArgumentMatchers.any(BigDecimal.class),
            org.mockito.ArgumentMatchers.eq("JPY")))
        .thenReturn(0);

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                command(
                    new BigDecimal("15000"),
                    new BigDecimal("100"),
                    new BigDecimal("0.0066666667"),
                    null,
                    null)));
  }


  @Test
  void crossCurrencyBuyUsesExistingTargetCostCurrencyAndDisposesSourcePosition() {
    Fixture f = fixture();
    Account cny = account("cny", "CNY");
    Account usd = account("usd", "USD");
    when(f.accountMapper.selectById("cny")).thenReturn(cny);
    when(f.accountMapper.selectById("usd")).thenReturn(usd);
    when(f.accountMapper.addBalance("cny", new BigDecimal("-500"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("3.5"))).thenReturn(1);

    FxPosition cnyPosition =
        position("cny", "CNY", new BigDecimal("1000"), new BigDecimal("20000"), "JPY", new BigDecimal("20"));
    FxPosition usdPosition =
        position("usd", "USD", new BigDecimal("100"), new BigDecimal("15000"), "JPY", new BigDecimal("150"));
    when(f.fxPositionMapper.selectByAccountIdForUpdate("cny")).thenReturn(cnyPosition);
    when(f.fxPositionMapper.selectByAccountIdForUpdate("usd")).thenReturn(usdPosition);
    when(f.exchangeRateService.effective(any(LocalDate.class), eq("CNY"), eq("JPY"), isNull()))
        .thenReturn(rate("CNY", "JPY", "20"));
    when(f.fxPositionMapper.applySellPosition(
            "cny", new BigDecimal("500"), new BigDecimal("10000.0000"), false))
        .thenReturn(1);
    when(f.fxPositionMapper.upsertBuyPosition(
            any(String.class),
            eq("usd"),
            eq("USD"),
            eq(new BigDecimal("3.5")),
            eq(new BigDecimal("10000.0000")),
            eq("JPY")))
        .thenReturn(1);
    when(f.fxPositionMovementMapper.insert(any(FxPositionMovement.class))).thenReturn(1);

    var result =
        f.service.create(
            crossCommand(
                "cny", "CNY", "usd", "USD", new BigDecimal("500"), new BigDecimal("3.5")));

    verify(f.fxPositionMapper)
        .applySellPosition("cny", new BigDecimal("500"), new BigDecimal("10000.0000"), false);
    verify(f.fxPositionMapper)
        .upsertBuyPosition(
            any(String.class),
            eq("usd"),
            eq("USD"),
            eq(new BigDecimal("3.5")),
            eq(new BigDecimal("10000.0000")),
            eq("JPY"));
    assertEquals(new BigDecimal("10000.0000"), result.costBasisAmount());
    assertEquals(new BigDecimal("0.0000"), result.realizedGainLoss());
    assertEquals("JPY", result.gainLossCurrency());
  }

  @Test
  void laterJpyBuyAddsIntoSameUsdJpyCostBasisForWeightedAverage() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-10000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("65"))).thenReturn(1);

    FxPosition existingUsd =
        position("usd", "USD", new BigDecimal("70"), new BigDecimal("10000"), "JPY", new BigDecimal("142.8571428571"));
    when(f.fxPositionMapper.selectByAccountIdForUpdate("jpy")).thenReturn(null);
    when(f.fxPositionMapper.selectByAccountIdForUpdate("usd")).thenReturn(existingUsd);
    when(f.fxPositionMapper.upsertBuyPosition(
            any(String.class),
            eq("usd"),
            eq("USD"),
            eq(new BigDecimal("65")),
            eq(new BigDecimal("10000.0000")),
            eq("JPY")))
        .thenReturn(1);
    when(f.fxPositionMovementMapper.insert(any(FxPositionMovement.class))).thenReturn(1);

    f.service.create(
        crossCommand(
            "jpy", "JPY", "usd", "USD", new BigDecimal("10000"), new BigDecimal("65")));

    verify(f.fxPositionMapper)
        .upsertBuyPosition(
            any(String.class),
            eq("usd"),
            eq("USD"),
            eq(new BigDecimal("65")),
            eq(new BigDecimal("10000.0000")),
            eq("JPY"));
    // Mapper's atomic UPSERT then yields: quantity 135, total cost 20,000 JPY,
    // average cost 148.1481481481 JPY/USD.
  }

  @Test
  void thirdCurrencyFeeIsRejectedBeforeWrites() {
    Fixture f = fixture();

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                command(
                    new BigDecimal("15000"),
                    new BigDecimal("100"),
                    new BigDecimal("0.0066666667"),
                    BigDecimal.ONE,
                    "EUR")));

    verify(f.transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test
  void sellForeignCalculatesCostBasisAndRealizedGainLossWithoutFee() {
    Fixture f = sellFixture(new BigDecimal("300"), new BigDecimal("42000"), new BigDecimal("140"));
    when(f.accountMapper.addBalance("usd", new BigDecimal("-100"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("15500"))).thenReturn(1);
    when(f.fxPositionMapper.applySellPosition(
            "usd", new BigDecimal("100"), new BigDecimal("14000.0000"), false))
        .thenReturn(1);

    var result = f.service.create(sellCommand(new BigDecimal("100"), new BigDecimal("15500"), new BigDecimal("155"), null, null));

    assertEquals("SELL_FOREIGN", result.fxDirectionCode());
    assertEquals(new BigDecimal("14000.0000"), result.costBasisAmount());
    assertEquals(new BigDecimal("1500.0000"), result.realizedGainLoss());
    assertEquals("JPY", result.gainLossCurrency());

    ArgumentCaptor<FxDetail> detailCaptor = ArgumentCaptor.forClass(FxDetail.class);
    verify(f.fxDetailMapper).insert(detailCaptor.capture());
    assertEquals(new BigDecimal("14000.0000"), detailCaptor.getValue().getCostBasisAmount());
    assertEquals(new BigDecimal("1500.0000"), detailCaptor.getValue().getRealizedGainLoss());
    verify(f.transactionEntryMapper, Mockito.times(2)).insert(any(TransactionEntry.class));
  }

  @Test
  void sellForeignLossIsRecordedWithoutSyntheticGainLossEntry() {
    Fixture f = sellFixture(new BigDecimal("300"), new BigDecimal("42000"), new BigDecimal("140"));
    when(f.accountMapper.addBalance("usd", new BigDecimal("-100"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("13000"))).thenReturn(1);
    when(f.fxPositionMapper.applySellPosition(
            "usd", new BigDecimal("100"), new BigDecimal("14000.0000"), false))
        .thenReturn(1);

    var result =
        f.service.create(
            sellCommand(
                new BigDecimal("100"),
                new BigDecimal("13000"),
                new BigDecimal("130"),
                null,
                null));

    assertEquals(new BigDecimal("14000.0000"), result.costBasisAmount());
    assertEquals(new BigDecimal("-1000.0000"), result.realizedGainLoss());
    assertEquals("JPY", result.gainLossCurrency());

    ArgumentCaptor<TransactionEntry> entryCaptor = ArgumentCaptor.forClass(TransactionEntry.class);
    verify(f.transactionEntryMapper, Mockito.times(2)).insert(entryCaptor.capture());
    var entries = entryCaptor.getAllValues();
    assertEquals("SOURCE", entries.get(0).getEntryRole());
    assertEquals(new BigDecimal("-100"), entries.get(0).getAmount());
    assertEquals("TARGET", entries.get(1).getEntryRole());
    assertEquals(new BigDecimal("13000"), entries.get(1).getAmount());
    assertEquals(
        0,
        entries.stream()
            .filter(entry -> "FX_GAIN_LOSS".equals(entry.getEntryRole()))
            .count());
  }

  @Test
  void partialSellPostsOnlyRealAccountBalanceChangesAndKeepsPositionOpen() {
    Fixture f = sellFixture(new BigDecimal("300"), new BigDecimal("42000"), new BigDecimal("140"));
    when(f.accountMapper.addBalance("usd", new BigDecimal("-100"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("15500"))).thenReturn(1);
    when(f.fxPositionMapper.applySellPosition(
            "usd", new BigDecimal("100"), new BigDecimal("14000.0000"), false))
        .thenReturn(1);

    f.service.create(
        sellCommand(
            new BigDecimal("100"),
            new BigDecimal("15500"),
            new BigDecimal("155"),
            null,
            null));

    verify(f.accountMapper).addBalance("usd", new BigDecimal("-100"));
    verify(f.accountMapper).addBalance("jpy", new BigDecimal("15500"));
    verify(f.fxPositionMapper)
        .applySellPosition(
            "usd", new BigDecimal("100"), new BigDecimal("14000.0000"), false);
  }

  @Test
  void targetCurrencyFeeReducesSellRealizedGainLoss() {
    Fixture f = sellFixture(new BigDecimal("300"), new BigDecimal("42000"), new BigDecimal("140"));
    when(f.accountMapper.addBalance("usd", new BigDecimal("-100"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("15500"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-500"))).thenReturn(1);
    when(f.fxPositionMapper.applySellPosition(
            "usd", new BigDecimal("100"), new BigDecimal("14000.0000"), false))
        .thenReturn(1);

    var result = f.service.create(sellCommand(new BigDecimal("100"), new BigDecimal("15500"), new BigDecimal("155"), new BigDecimal("500"), "JPY"));

    assertEquals(new BigDecimal("1000.0000"), result.realizedGainLoss());
    verify(f.accountMapper).addBalance("jpy", new BigDecimal("-500"));
    verify(f.transactionEntryMapper, Mockito.times(3)).insert(any(TransactionEntry.class));
  }

  @Test
  void sourceCurrencyFeeIncreasesDisposedQuantityAndCostBasis() {
    Fixture f = sellFixture(new BigDecimal("300"), new BigDecimal("42000"), new BigDecimal("140"));
    when(f.accountMapper.addBalance("usd", new BigDecimal("-100"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("-1"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("15500"))).thenReturn(1);
    when(f.fxPositionMapper.applySellPosition(
            "usd", new BigDecimal("101"), new BigDecimal("14140.0000"), false))
        .thenReturn(1);

    var result = f.service.create(sellCommand(new BigDecimal("100"), new BigDecimal("15500"), new BigDecimal("155"), BigDecimal.ONE, "USD"));

    assertEquals(new BigDecimal("14140.0000"), result.costBasisAmount());
    assertEquals(new BigDecimal("1360.0000"), result.realizedGainLoss());
    verify(f.fxPositionMapper).applySellPosition("usd", new BigDecimal("101"), new BigDecimal("14140.0000"), false);
  }

  @Test
  void sellRejectsOversellIncludingSourceCurrencyFeeBeforeWrites() {
    Fixture f = sellFixture(new BigDecimal("100"), new BigDecimal("14000"), new BigDecimal("140"));

    assertThrows(
        BusinessException.class,
        () -> f.service.create(sellCommand(new BigDecimal("100"), new BigDecimal("15500"), new BigDecimal("155"), BigDecimal.ONE, "USD")));

    verify(f.transactionMapper, never()).insert(any(Transaction.class));
    verify(f.fxPositionMapper, never()).applySellPosition(any(String.class), any(BigDecimal.class), any(BigDecimal.class), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void fullSellUsesExactRemainingTotalCostAndClosesPosition() {
    Fixture f = sellFixture(new BigDecimal("100"), new BigDecimal("14000.1234"), new BigDecimal("140.0012340000"));
    when(f.accountMapper.addBalance("usd", new BigDecimal("-100"))).thenReturn(1);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("15000"))).thenReturn(1);
    when(f.fxPositionMapper.applySellPosition(
            "usd", new BigDecimal("100"), new BigDecimal("14000.1234"), true))
        .thenReturn(1);

    var result = f.service.create(sellCommand(new BigDecimal("100"), new BigDecimal("15000"), new BigDecimal("150"), null, null));

    assertEquals(new BigDecimal("14000.1234"), result.costBasisAmount());
    assertEquals(new BigDecimal("999.8766"), result.realizedGainLoss());
    verify(f.fxPositionMapper).applySellPosition("usd", new BigDecimal("100"), new BigDecimal("14000.1234"), true);
  }

  @Test
  void sellMustReturnToPositionCostCurrency() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.selectById("eur")).thenReturn(account("eur", "EUR"));
    when(f.fxPositionMapper.selectByAccountIdForUpdate("usd"))
        .thenReturn(position("usd", "USD", new BigDecimal("100"), new BigDecimal("14000"), "JPY", new BigDecimal("140")));

    FxExchangeCreateVo command = new FxExchangeCreateVo(
        "SELL_FOREIGN", "usd", "eur", new BigDecimal("100"), "USD",
        new BigDecimal("90"), "EUR", new BigDecimal("0.9"), null, null, null, Instant.now());

    assertThrows(BusinessException.class, () -> f.service.create(command));
    verify(f.transactionMapper, never()).insert(any(Transaction.class));
  }

  @Test
  void executedRateIsDerivedFromActualSourceAndTargetAmounts() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    allowPositionUpsert(f);

    f.service.create(
        command(
            new BigDecimal("15000"),
            new BigDecimal("100"),
            new BigDecimal("150"),
            null,
            null));

    ArgumentCaptor<FxDetail> detail = ArgumentCaptor.forClass(FxDetail.class);
    verify(f.fxDetailMapper).insert(detail.capture());
    assertEquals(new BigDecimal("0.0066666667"), detail.getValue().getExecutedRate());
  }

  @Test
  void bankAccountWithDifferentCurrencyCanBeBuyForeignTarget() {
    Fixture f = fixture();
    Account jpyBank = account("jpy", "JPY");
    jpyBank.setAccountTypeCode("BANK");
    Account usdBank = account("usd", "USD");
    usdBank.setAccountTypeCode("BANK");

    when(f.accountMapper.selectById("jpy")).thenReturn(jpyBank);
    when(f.accountMapper.selectById("usd")).thenReturn(usdBank);
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    allowPositionUpsert(f);

    var result =
        f.service.create(
            command(
                new BigDecimal("15000"),
                new BigDecimal("100"),
                new BigDecimal("0.0066666667"),
                null,
                null));

    assertEquals("BUY_FOREIGN", result.fxDirectionCode());
    verify(f.fxPositionMapper)
        .upsertBuyPosition(
            any(String.class),
            org.mockito.ArgumentMatchers.eq("usd"),
            org.mockito.ArgumentMatchers.eq("USD"),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("100")),
            org.mockito.ArgumentMatchers.eq(new BigDecimal("15000.0000")),
            org.mockito.ArgumentMatchers.eq("JPY"));
  }

  @Test
  void sourceAccountMustHaveEnoughBalanceIncludingSourceCurrencyFee() {
    Fixture f = fixture();
    Account jpy = account("jpy", "JPY");
    jpy.setTotalAmount(new BigDecimal("15299"));
    when(f.accountMapper.selectById("jpy")).thenReturn(jpy);
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                command(
                    new BigDecimal("15000"),
                    new BigDecimal("100"),
                    new BigDecimal("0.0066666667"),
                    new BigDecimal("300"),
                    "JPY")));

    verifyNoInteractions(f.transactionMapper, f.transactionEntryMapper, f.fxDetailMapper, f.fxPositionMapper);
  }

  @Test
  void accountCurrencyMismatchIsRejectedBeforeWrites() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "USD"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                command(
                    new BigDecimal("15000"),
                    new BigDecimal("100"),
                    new BigDecimal("0.0066666667"),
                    null,
                    null)));

    verifyNoInteractions(f.transactionMapper, f.transactionEntryMapper, f.fxDetailMapper, f.fxPositionMapper);
  }

  @Test
  void disabledAccountIsRejectedBeforeWrites() {
    Fixture f = fixture();
    Account source = account("jpy", "JPY");
    source.setEnabled(false);
    when(f.accountMapper.selectById("jpy")).thenReturn(source);
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                command(
                    new BigDecimal("15000"),
                    new BigDecimal("100"),
                    new BigDecimal("0.0066666667"),
                    null,
                    null)));

    verifyNoInteractions(f.transactionMapper, f.transactionEntryMapper, f.fxDetailMapper, f.fxPositionMapper);
  }

  @Test
  void positionFailurePropagatesSoTransactionalBoundaryCanRollbackAllPriorWrites() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.addBalance("jpy", new BigDecimal("-15000"))).thenReturn(1);
    when(f.accountMapper.addBalance("usd", new BigDecimal("100"))).thenReturn(1);
    when(f.fxPositionMapper.upsertBuyPosition(
            any(String.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(BigDecimal.class),
            org.mockito.ArgumentMatchers.any(BigDecimal.class),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(0);

    assertThrows(
        BusinessException.class,
        () ->
            f.service.create(
                command(
                    new BigDecimal("15000"),
                    new BigDecimal("100"),
                    new BigDecimal("0.0066666667"),
                    null,
                    null)));

    verify(f.transactionMapper).insert(any(Transaction.class));
    verify(f.transactionEntryMapper, Mockito.times(2)).insert(any(TransactionEntry.class));
    verify(f.fxPositionMapper)
        .upsertBuyPosition(
            any(String.class),
            eq("usd"),
            eq("USD"),
            eq(new BigDecimal("100")),
            eq(new BigDecimal("15000.0000")),
            eq("JPY"));
    verify(f.fxDetailMapper, never()).insert(any(FxDetail.class));
    verifyNoInteractions(f.fxPositionMovementMapper);
  }

  @Test
  void getPositionSupportsBankAssetAccountRegardlessOfAccountType() {
    Fixture f = fixture();
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));

    com.jiajunxie.entity.FxPosition position = new com.jiajunxie.entity.FxPosition();
    position.setPositionId("position");
    position.setAccountId("usd");
    position.setCurrency("USD");
    position.setQuantity(new BigDecimal("150"));
    position.setTotalCost(new BigDecimal("23000"));
    position.setCostCurrency("JPY");
    position.setAverageCostRate(new BigDecimal("153.3333333333"));
    when(f.fxPositionMapper.selectByAccountId("usd")).thenReturn(position);

    var result = f.service.getPosition("usd");

    assertEquals(new BigDecimal("150"), result.quantity());
    assertEquals(new BigDecimal("23000"), result.totalCost());
    assertEquals("JPY", result.costCurrency());
  }

  private Fixture fixture() {
    TransactionMapper transactionMapper = Mockito.mock(TransactionMapper.class);
    TransactionEntryMapper transactionEntryMapper = Mockito.mock(TransactionEntryMapper.class);
    AccountMapper accountMapper = Mockito.mock(AccountMapper.class);
    FxDetailMapper fxDetailMapper = Mockito.mock(FxDetailMapper.class);
    FxPositionMapper fxPositionMapper = Mockito.mock(FxPositionMapper.class);
    com.jiajunxie.mapper.FxPositionMovementMapper fxPositionMovementMapper =
        Mockito.mock(com.jiajunxie.mapper.FxPositionMovementMapper.class);
    com.jiajunxie.service.ExchangeRateService exchangeRateService =
        Mockito.mock(com.jiajunxie.service.ExchangeRateService.class);
    CurrencyInventoryLifecycleEngine currencyInventoryLifecycleEngine =
        Mockito.mock(CurrencyInventoryLifecycleEngine.class);
    com.jiajunxie.mapper.CurrencyInventoryBucketMapper currencyInventoryBucketMapper =
        Mockito.mock(com.jiajunxie.mapper.CurrencyInventoryBucketMapper.class);
    when(currencyInventoryLifecycleEngine.isActive()).thenReturn(false);

    return new Fixture(
        new FxExchangeServiceImpl(
            transactionMapper,
            transactionEntryMapper,
            accountMapper,
            fxDetailMapper,
            fxPositionMapper,
            exchangeRateService,
            fxPositionMovementMapper,
            currencyInventoryLifecycleEngine,
            currencyInventoryBucketMapper),
        transactionMapper,
        transactionEntryMapper,
        accountMapper,
        fxDetailMapper,
        fxPositionMapper,
        exchangeRateService,
        fxPositionMovementMapper);
  }

  private FxExchangeCreateVo command(
      BigDecimal sourceAmount,
      BigDecimal targetAmount,
      BigDecimal rate,
      BigDecimal feeAmount,
      String feeCurrency) {
    return new FxExchangeCreateVo(
        "BUY_FOREIGN",
        "jpy",
        "usd",
        sourceAmount,
        "JPY",
        targetAmount,
        "USD",
        rate,
        feeAmount,
        feeCurrency,
        "JPY 买入 USD",
        Instant.parse("2026-08-10T00:00:00Z"));
  }

  private FxExchangeCreateVo crossCommand(
      String sourceId,
      String sourceCurrency,
      String targetId,
      String targetCurrency,
      BigDecimal sourceAmount,
      BigDecimal targetAmount) {
    return new FxExchangeCreateVo(
        "BUY_FOREIGN",
        sourceId,
        targetId,
        sourceAmount,
        sourceCurrency,
        targetAmount,
        targetCurrency,
        targetAmount.divide(sourceAmount, 10, java.math.RoundingMode.HALF_UP),
        null,
        null,
        sourceCurrency + " 买入 " + targetCurrency,
        Instant.parse("2026-08-10T00:00:00Z"));
  }

  private FxExchangeCreateVo sellCommand(
      BigDecimal sourceAmount,
      BigDecimal targetAmount,
      BigDecimal rate,
      BigDecimal feeAmount,
      String feeCurrency) {
    return new FxExchangeCreateVo(
        "SELL_FOREIGN",
        "usd",
        "jpy",
        sourceAmount,
        "USD",
        targetAmount,
        "JPY",
        rate,
        feeAmount,
        feeCurrency,
        "USD 卖出为 JPY",
        Instant.parse("2026-08-10T00:00:00Z"));
  }

  private Fixture sellFixture(BigDecimal quantity, BigDecimal totalCost, BigDecimal averageCostRate) {
    Fixture f = fixture();
    when(f.accountMapper.selectById("usd")).thenReturn(account("usd", "USD"));
    when(f.accountMapper.selectById("jpy")).thenReturn(account("jpy", "JPY"));
    when(f.fxPositionMapper.selectByAccountIdForUpdate("usd"))
        .thenReturn(position("usd", "USD", quantity, totalCost, "JPY", averageCostRate));
    return f;
  }

  private FxPosition position(
      String accountId, String currency, BigDecimal quantity, BigDecimal totalCost,
      String costCurrency, BigDecimal averageCostRate) {
    FxPosition position = new FxPosition();
    position.setPositionId("position-" + accountId);
    position.setAccountId(accountId);
    position.setCurrency(currency);
    position.setQuantity(quantity);
    position.setTotalCost(totalCost);
    position.setCostCurrency(costCurrency);
    position.setAverageCostRate(averageCostRate);
    return position;
  }

  private Account account(String id, String currency) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountClassCode("ASSET");
    account.setAccountTypeCode("BANK");
    account.setCurrency(currency);
    account.setEnabled(true);
    account.setTotalAmount(new BigDecimal("1000000"));
    return account;
  }

  private void allowPositionUpsert(Fixture fixture) {
    when(fixture.fxPositionMapper.selectByAccountIdForUpdate(any(String.class))).thenReturn(null);
    when(fixture.fxPositionMapper.upsertBuyPosition(
            any(String.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(BigDecimal.class),
            org.mockito.ArgumentMatchers.any(BigDecimal.class),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(1);
    when(fixture.fxPositionMovementMapper.insert(any(FxPositionMovement.class))).thenReturn(1);
  }

  private com.jiajunxie.vo.ExchangeRateVo rate(String base, String quote, String value) {
    return new com.jiajunxie.vo.ExchangeRateVo(
        "rate",
        LocalDate.of(2026, 8, 10),
        base,
        quote,
        new BigDecimal(value),
        "MANUAL",
        Instant.now(),
        false);
  }

  private record Fixture(
      FxExchangeServiceImpl service,
      TransactionMapper transactionMapper,
      TransactionEntryMapper transactionEntryMapper,
      AccountMapper accountMapper,
      FxDetailMapper fxDetailMapper,
      FxPositionMapper fxPositionMapper,
      com.jiajunxie.service.ExchangeRateService exchangeRateService,
      com.jiajunxie.mapper.FxPositionMovementMapper fxPositionMovementMapper) {}
}
