package com.jiajunxie.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionLedgerRow(
    String transactionId,
    Instant transactionTime,
    Instant createdAt,
    String transactionTypeCode,
    String transactionTypeName,
    String originCode,
    String originReferenceId,
    String merchant,
    String remark,
    String categoryId,
    String categoryName,
    String currency,
    BigDecimal displayAmount,
    String flowDirection,
    TransactionNature transactionNature,
    String fxDirectionCode,
    BigDecimal fxFeeAmount,
    String fxFeeCurrency,
    List<TransactionLedgerAccountSummary> accountSummaries,
    List<TransactionLedgerPersonAllocation> personAllocations,
    boolean readonly,
    String readonlyReason) {}
