package com.jiajunxie.vo;

import java.time.Instant;
import java.util.List;

public record TransactionCreateVo(
    String transactionId,
    String transactionType,
    String categoryId,
    String merchant,
    String remark,
    Instant transactionTime,
    List<TransactionEntryVo> entries,
    List<AllocationVo> allocations,
    TransactionPaymentVo payment,
    List<StatementPaymentAllocationCommandVo> paymentAllocations) {}

