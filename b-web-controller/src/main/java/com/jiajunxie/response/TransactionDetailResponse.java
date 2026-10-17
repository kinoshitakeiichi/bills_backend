package com.jiajunxie.response;

import java.time.Instant;
import java.util.List;

public record TransactionDetailResponse(
    String transactionId,
    String transactionType,
    String categoryId,
    String merchant,
    String remark,
    Instant transactionTime,
    List<TransactionEntryResponse> entries,
    List<TransactionAllocationResponse> allocations,
    InstallmentPlanResponse paymentPlan) {}
