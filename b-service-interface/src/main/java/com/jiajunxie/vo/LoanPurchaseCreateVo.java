package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LoanPurchaseCreateVo(
    String loanAccountId,
    String categoryId,
    BigDecimal amount,
    String currency,
    String merchant,
    String remark,
    Instant transactionTime,
    List<AllocationVo> allocations) {}
