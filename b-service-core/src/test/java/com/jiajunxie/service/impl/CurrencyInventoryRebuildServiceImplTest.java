package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.domain.inventory.CurrencyInventoryAccountSnapshot;
import com.jiajunxie.domain.inventory.CurrencyInventoryReplayRow;
import com.jiajunxie.entity.CurrencyInventoryBucket;
import com.jiajunxie.entity.FxDetail;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.FxDetailMapper;
import com.jiajunxie.vo.CurrencyInventoryAccountPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildApplyRequestVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildApplyResultVo;
import com.jiajunxie.vo.CurrencyInventoryBucketPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildRequestVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrencyInventoryRebuildServiceImplTest {
  private static final String CNY = "cny-account";
  private static final String JPY = "jpy-account";
  private static final String USD = "usd-account";
  private static final String EUR = "eur-account";

  @Test
  void previewRebuildsOnlyDirectFxCostBucketsAndKeepsNonFxAsResidual() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        service(rebuildMapper, fxDetailMapper, false);

    when(rebuildMapper.selectAssetAccounts())
        .thenReturn(
            List.of(
                account(CNY, "CNY", "4400"),
                account(JPY, "JPY", "55000"),
                account(USD, "USD", "700"),
                account(EUR, "EUR", "630")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of(USD, EUR));

    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("t01", "INCOME", 1, CNY, "10000", "CNY"),
                row("t02", "INCOME", 2, JPY, "100000", "JPY"),
                row("t03", "INCOME", 3, USD, "100", "USD"), // opening-like non-FX
                row("t04", "INCOME", 4, USD, "100", "USD"), // interest-like non-FX
                row("t05", "EXCHANGE", 5, CNY, "-5600", "CNY"),
                row("t05", "EXCHANGE", 5, USD, "700", "USD"),
                row("t06", "EXCHANGE", 6, JPY, "-45000", "JPY"),
                row("t06", "EXCHANGE", 6, USD, "500", "USD"),
                row("t07", "EXCHANGE", 7, USD, "-700", "USD"),
                row("t07", "EXCHANGE", 7, EUR, "630", "EUR")));

    when(fxDetailMapper.selectById("t05"))
        .thenReturn(fx("t05", CNY, USD, "5600", "CNY", "700", "USD"));
    when(fxDetailMapper.selectById("t06"))
        .thenReturn(fx("t06", JPY, USD, "45000", "JPY", "500", "USD"));
    when(fxDetailMapper.selectById("t07"))
        .thenReturn(fx("t07", USD, EUR, "700", "USD", "630", "EUR"));

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of(USD, EUR)));

    assertTrue(preview.applyEligible());
    assertEquals(0, preview.globalIssueCount());
    assertEquals(7, preview.replayTransactionCount());

    CurrencyInventoryAccountPreviewVo usd = account(preview, USD);
    assertEquals(new BigDecimal("700.0000"), usd.accountBalance());
    assertEquals(new BigDecimal("600.0000"), usd.fxQuantity());
    assertEquals(new BigDecimal("100.0000"), usd.nonFxQuantity());
    assertTrue(usd.accountMatchesLedger());
    assertTrue(usd.fxWithinAccountBalance());

    CurrencyInventoryBucketPreviewVo cnyBucket = bucket(usd, "CNY");
    assertEquals(new BigDecimal("350.0000"), cnyBucket.quantity());
    assertEquals(new BigDecimal("2800.0000"), cnyBucket.sourceCostAmount());
    assertEquals(new BigDecimal("8.0000000000"), cnyBucket.averageCostRate());

    CurrencyInventoryBucketPreviewVo jpyBucket = bucket(usd, "JPY");
    assertEquals(new BigDecimal("250.0000"), jpyBucket.quantity());
    assertEquals(new BigDecimal("22500.0000"), jpyBucket.sourceCostAmount());
    assertEquals(new BigDecimal("90.0000000000"), jpyBucket.averageCostRate());

    CurrencyInventoryAccountPreviewVo eur = account(preview, EUR);
    assertEquals(new BigDecimal("630.0000"), eur.fxQuantity());
    assertEquals(new BigDecimal("0.0000"), eur.nonFxQuantity());
    CurrencyInventoryBucketPreviewVo usdBucket = bucket(eur, "USD");
    assertEquals(new BigDecimal("630.0000"), usdBucket.quantity());
    assertEquals(new BigDecimal("700.0000"), usdBucket.sourceCostAmount());
    assertEquals(new BigDecimal("1.1111111111"), usdBucket.averageCostRate());
  }

  @Test
  void sameCurrencyTransferMovesOnlyTheFxCostShareAndLeavesNonFxImplicit() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        service(rebuildMapper, fxDetailMapper, false);

    String source = "usd-a";
    String target = "usd-b";
    when(rebuildMapper.selectAssetAccounts())
        .thenReturn(List.of(account(JPY, "JPY", "50000"), account(source, "USD", "600"), account(target, "USD", "400")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of(source, target));
    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("t01", "INCOME", 1, JPY, "100000", "JPY"),
                row("t02", "INCOME", 2, source, "500", "USD"),
                row("t03", "EXCHANGE", 3, JPY, "-50000", "JPY"),
                row("t03", "EXCHANGE", 3, source, "500", "USD"),
                transferRow("t04", 4, source, "-400", "USD", "SOURCE"),
                transferRow("t04", 4, target, "400", "USD", "TARGET")));
    when(fxDetailMapper.selectById("t03"))
        .thenReturn(fx("t03", JPY, source, "50000", "JPY", "500", "USD"));

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of(source, target)));

    assertTrue(preview.applyEligible());
    CurrencyInventoryAccountPreviewVo a = account(preview, source);
    CurrencyInventoryAccountPreviewVo b = account(preview, target);

    // Before transfer source was 1000 USD = 500 FX + 500 non-FX. A 400 transfer carries 200 FX.
    assertEquals(new BigDecimal("300.0000"), a.fxQuantity());
    assertEquals(new BigDecimal("300.0000"), a.nonFxQuantity());
    assertEquals(new BigDecimal("200.0000"), b.fxQuantity());
    assertEquals(new BigDecimal("200.0000"), b.nonFxQuantity());
    assertEquals(new BigDecimal("30000.0000"), bucket(a, "JPY").sourceCostAmount());
    assertEquals(new BigDecimal("20000.0000"), bucket(b, "JPY").sourceCostAmount());
  }

  @Test
  void legacyOrdinaryCashflowOrderDoesNotBlockFxPreviewWhenAccountHasNoFxCost() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        service(rebuildMapper, fxDetailMapper, false);

    when(rebuildMapper.selectAssetAccounts()).thenReturn(List.of(account(JPY, "JPY", "694230")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of());
    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("t01", "EXPENSE", 1, JPY, "-155000", "JPY"),
                row("t02", "EXPENSE", 2, JPY, "-780", "JPY"),
                row("t03", "INCOME", 3, JPY, "850010", "JPY")));

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of(JPY)));

    assertTrue(preview.applyEligible());
    assertEquals(0, preview.globalIssueCount());
    CurrencyInventoryAccountPreviewVo jpy = account(preview, JPY);
    assertEquals(new BigDecimal("0.0000"), jpy.fxQuantity());
    assertEquals(new BigDecimal("694230.0000"), jpy.nonFxQuantity());
    assertTrue(jpy.fxBuckets().isEmpty());
  }

  @Test
  void historicalFxIntoAccountWithoutLegacyPositionDoesNotInventCutoverBucket() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        service(rebuildMapper, fxDetailMapper, false);

    when(rebuildMapper.selectAssetAccounts())
        .thenReturn(List.of(account(USD, "USD", "0"), account(JPY, "JPY", "100")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of());
    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("t01", "INCOME", 1, USD, "1", "USD"),
                row("t02", "EXCHANGE", 2, USD, "-1", "USD"),
                row("t02", "EXCHANGE", 2, JPY, "100", "JPY")));

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of(JPY)));

    assertTrue(preview.applyEligible());
    assertEquals(0, preview.globalIssueCount());
    CurrencyInventoryAccountPreviewVo jpy = account(preview, JPY);
    assertEquals(new BigDecimal("0.0000"), jpy.fxQuantity());
    assertEquals(new BigDecimal("100.0000"), jpy.nonFxQuantity());
    assertTrue(jpy.fxBuckets().isEmpty());
  }

  @Test
  void scopedPreviewIgnoresUnrelatedMigrationIssueButKeepsRequestedReport() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        service(rebuildMapper, fxDetailMapper, false);

    String badUsd = "bad-usd";
    String cleanJpy = "clean-jpy";
    String badJpySource = "bad-jpy-source";
    when(rebuildMapper.selectAssetAccounts())
        .thenReturn(
            List.of(
                account(badJpySource, "JPY", "0"),
                account(badUsd, "USD", "900"),
                account(cleanJpy, "JPY", "100")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of(badUsd));
    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("bad-1", "INCOME", 1, badJpySource, "100000", "JPY"),
                row("bad-2", "EXCHANGE", 2, badJpySource, "-100000", "JPY"),
                row("bad-2", "EXCHANGE", 2, badUsd, "1000", "USD"),
                row("bad-3", "EXPENSE", 3, badUsd, "-1200", "USD"),
                row("bad-4", "INCOME", 4, badUsd, "1100", "USD"),
                row("clean-1", "INCOME", 5, cleanJpy, "100", "JPY")));
    when(fxDetailMapper.selectById("bad-2"))
        .thenReturn(fx("bad-2", badJpySource, badUsd, "100000", "JPY", "1000", "USD"));

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of(cleanJpy)));

    assertTrue(preview.applyEligible());
    assertEquals(0, preview.globalIssueCount());
    assertEquals(1, preview.replayAccountCount());
    assertEquals(1, preview.replayTransactionCount());
    assertEquals(1, preview.accounts().size());
    assertEquals(cleanJpy, preview.accounts().getFirst().accountId());
  }

  @Test
  void ordinaryDisposalUsesWholeAccountBalanceAsProRataBasis() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        service(rebuildMapper, fxDetailMapper, false);

    when(rebuildMapper.selectAssetAccounts())
        .thenReturn(List.of(account(JPY, "JPY", "0"), account(USD, "USD", "500")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of(USD));
    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("t01", "INCOME", 1, JPY, "100000", "JPY"),
                row("t02", "INCOME", 2, USD, "500", "USD"),
                row("t03", "EXCHANGE", 3, JPY, "-100000", "JPY"),
                row("t03", "EXCHANGE", 3, USD, "1000", "USD"),
                row("t04", "EXPENSE", 4, USD, "-1000", "USD")));
    when(fxDetailMapper.selectById("t03"))
        .thenReturn(fx("t03", JPY, USD, "100000", "JPY", "1000", "USD"));

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of(USD)));

    assertTrue(preview.applyEligible());
    CurrencyInventoryAccountPreviewVo usd = account(preview, USD);
    // Before expense: 1500 total = 1000 FX + 500 non-FX. Spending 1000 consumes 2/3 of each.
    assertEquals(new BigDecimal("333.3333"), usd.fxQuantity());
    assertEquals(new BigDecimal("166.6667"), usd.nonFxQuantity());
    assertEquals(new BigDecimal("33333.3300"), bucket(usd, "JPY").sourceCostAmount());
  }

  @Test
  void impossibleHistoricalBalanceOnlyBlocksWhenItMakesFxDepletionAmbiguous() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        service(rebuildMapper, fxDetailMapper, false);

    when(rebuildMapper.selectAssetAccounts())
        .thenReturn(List.of(account(JPY, "JPY", "0"), account(USD, "USD", "900")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of(USD));
    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("t01", "INCOME", 1, JPY, "100000", "JPY"),
                row("t02", "EXCHANGE", 2, JPY, "-100000", "JPY"),
                row("t02", "EXCHANGE", 2, USD, "1000", "USD"),
                row("t03", "EXPENSE", 3, USD, "-1200", "USD"),
                row("t04", "INCOME", 4, USD, "1100", "USD")));
    when(fxDetailMapper.selectById("t02"))
        .thenReturn(fx("t02", JPY, USD, "100000", "JPY", "1000", "USD"));

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of(USD)));

    assertFalse(preview.applyEligible());
    assertTrue(
        preview.globalIssues().stream()
            .anyMatch(issue -> "FX_DEPLETION_BASIS_UNAVAILABLE".equals(issue.code())));
  }

  @Test
  void applyIsDisabledByDefaultUntilWritePathCutover() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryRebuildServiceImpl service = service(rebuildMapper, fxDetailMapper, false);

    BusinessException error =
        assertThrows(
            BusinessException.class,
            () ->
                service.apply(
                    new CurrencyInventoryRebuildApplyRequestVo(
                        "fingerprint", "APPLY_FX_COST_INVENTORY_V3")));

    assertEquals(40900, error.getCode());
  }

  @Test
  void applyRequiresReviewedFingerprintAndPersistsExactlyTheRecomputedFxBucket() {
    CurrencyInventoryRebuildMapper rebuildMapper = mock(CurrencyInventoryRebuildMapper.class);
    FxDetailMapper fxDetailMapper = mock(FxDetailMapper.class);
    CurrencyInventoryBucketMapper bucketMapper = mock(CurrencyInventoryBucketMapper.class);
    CurrencyInventoryRebuildServiceImpl service =
        new CurrencyInventoryRebuildServiceImpl(rebuildMapper, fxDetailMapper, bucketMapper, true);

    when(rebuildMapper.selectAssetAccounts())
        .thenReturn(List.of(account(JPY, "JPY", "0"), account(USD, "USD", "1000")));
    when(rebuildMapper.selectLegacyFxPositionAccountIds()).thenReturn(List.of(USD));
    when(rebuildMapper.selectAssetReplayRows())
        .thenReturn(
            List.of(
                row("t01", "INCOME", 1, JPY, "100000", "JPY"),
                row("t02", "EXCHANGE", 2, JPY, "-100000", "JPY"),
                row("t02", "EXCHANGE", 2, USD, "1000", "USD")));
    when(fxDetailMapper.selectById("t02"))
        .thenReturn(fx("t02", JPY, USD, "100000", "JPY", "1000", "USD"));
    when(bucketMapper.insert(any(CurrencyInventoryBucket.class))).thenReturn(1);
    when(rebuildMapper.insertInventoryCutoverRun(any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt()))
        .thenReturn(1);

    CurrencyInventoryRebuildPreviewVo preview =
        service.preview(new CurrencyInventoryRebuildRequestVo(List.of()));
    assertTrue(preview.applyEligible());
    assertEquals(64, preview.fingerprint().length());

    assertThrows(
        BusinessException.class,
        () ->
            service.apply(
                new CurrencyInventoryRebuildApplyRequestVo(
                    "0".repeat(64), "APPLY_FX_COST_INVENTORY_V3")));

    CurrencyInventoryRebuildApplyResultVo applied =
        service.apply(
            new CurrencyInventoryRebuildApplyRequestVo(
                preview.fingerprint(), "APPLY_FX_COST_INVENTORY_V3"));

    assertEquals(preview.fingerprint(), applied.fingerprint());
    assertEquals(1, applied.persistedBucketCount());
    assertEquals(0, applied.persistedMovementCount());
    verify(bucketMapper).insert(any(CurrencyInventoryBucket.class));
  }

  private CurrencyInventoryRebuildServiceImpl service(
      CurrencyInventoryRebuildMapper rebuildMapper,
      FxDetailMapper fxDetailMapper,
      boolean applyEnabled) {
    return new CurrencyInventoryRebuildServiceImpl(
        rebuildMapper, fxDetailMapper, mock(CurrencyInventoryBucketMapper.class), applyEnabled);
  }

  private CurrencyInventoryAccountSnapshot account(String id, String currency, String balance) {
    CurrencyInventoryAccountSnapshot snapshot = new CurrencyInventoryAccountSnapshot();
    snapshot.setAccountId(id);
    snapshot.setAccountName(id);
    snapshot.setCurrency(currency);
    snapshot.setAccountBalance(new BigDecimal(balance));
    snapshot.setLedgerBalance(new BigDecimal(balance));
    return snapshot;
  }

  private CurrencyInventoryReplayRow row(
      String transactionId,
      String type,
      int second,
      String accountId,
      String amount,
      String currency) {
    CurrencyInventoryReplayRow row = new CurrencyInventoryReplayRow();
    row.setTransactionId(transactionId);
    row.setTransactionType(type);
    row.setTransactionTime(Instant.parse("2099-01-01T00:00:" + String.format("%02d", second) + "Z"));
    row.setTransactionCreatedAt(row.getTransactionTime());
    row.setEntryId(transactionId + "-" + accountId);
    row.setAccountId(accountId);
    row.setAmount(new BigDecimal(amount));
    row.setEntryCurrency(currency);
    row.setEntryRole(new BigDecimal(amount).signum() < 0 ? "SOURCE" : "TARGET");
    row.setEntryCreatedAt(row.getTransactionTime());
    row.setAccountName(accountId);
    row.setAccountCurrency(currency);
    return row;
  }

  private CurrencyInventoryReplayRow transferRow(
      String transactionId,
      int second,
      String accountId,
      String amount,
      String currency,
      String role) {
    CurrencyInventoryReplayRow row = row(transactionId, "TRANSFER", second, accountId, amount, currency);
    row.setEntryRole(role);
    return row;
  }

  private FxDetail fx(
      String transactionId,
      String sourceAccountId,
      String targetAccountId,
      String sourceAmount,
      String sourceCurrency,
      String targetAmount,
      String targetCurrency) {
    FxDetail detail = new FxDetail();
    detail.setTransactionId(transactionId);
    detail.setFxDirectionCode("BUY_FOREIGN");
    detail.setSourceAccountId(sourceAccountId);
    detail.setTargetAccountId(targetAccountId);
    detail.setSourceAmount(new BigDecimal(sourceAmount));
    detail.setSourceCurrency(sourceCurrency);
    detail.setTargetAmount(new BigDecimal(targetAmount));
    detail.setTargetCurrency(targetCurrency);
    detail.setExecutedRate(
        new BigDecimal(targetAmount)
            .divide(new BigDecimal(sourceAmount), 10, java.math.RoundingMode.HALF_UP));
    return detail;
  }

  private CurrencyInventoryAccountPreviewVo account(
      CurrencyInventoryRebuildPreviewVo preview, String accountId) {
    return preview.accounts().stream()
        .filter(account -> account.accountId().equals(accountId))
        .findFirst()
        .orElseThrow();
  }

  private CurrencyInventoryBucketPreviewVo bucket(
      CurrencyInventoryAccountPreviewVo account, String sourceCurrency) {
    return account.fxBuckets().stream()
        .filter(bucket -> "FX_EXCHANGE".equals(bucket.originTypeCode()))
        .filter(bucket -> sourceCurrency.equals(bucket.sourceCurrency()))
        .findFirst()
        .orElseThrow();
  }
}
