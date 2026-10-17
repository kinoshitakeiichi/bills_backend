package com.jiajunxie.vo;

import java.time.Instant;
import java.util.List;

public record TransactionDetailVo(
    String transactionId,
    String transactionType,
    String categoryId,
    String merchant,
    String remark,
    Instant transactionTime,
    List<TransactionEntryVo> entries,
    List<AllocationVo> allocations,
    InstallmentPlanVo paymentPlan) {}
