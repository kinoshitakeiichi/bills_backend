package com.jiajunxie.domain.transaction;

import java.math.BigDecimal;

public record TransactionLedgerAccountSummary(
    String accountId,
    String accountName,
    String accountCode,
    String accountTypeCode,
    String accountClassCode,
    BigDecimal amount,
    String currency,
    String entryRoleCode) {}
