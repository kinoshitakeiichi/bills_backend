package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * FX Domain V3 current FX-cost reconstruction for one ASSET account.
 *
 * <p>The account/ledger balance remains authoritative. {@code fxQuantity} is only the part of the
 * current balance that still carries direct FX acquisition cost; {@code nonFxQuantity} is the
 * residual current balance that is not represented by an FX cost bucket.
 */
public record CurrencyInventoryAccountPreviewVo(
    String accountId,
    String accountName,
    String currency,
    BigDecimal accountBalance,
    BigDecimal ledgerBalance,
    BigDecimal fxQuantity,
    BigDecimal nonFxQuantity,
    BigDecimal accountLedgerDifference,
    boolean accountMatchesLedger,
    boolean fxWithinAccountBalance,
    List<CurrencyInventoryBucketPreviewVo> fxBuckets,
    List<CurrencyInventoryRebuildIssueVo> issues) {}
