package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;

public record LoanPaymentCreateVo(
    String loanAccountId,
    String sourceAccountId,
    List<String> scheduleIds,
    BigDecimal principalAmount,
    BigDecimal interestAmount,
    BigDecimal feeAmount,
    String currency,
    String remark,
    Instant transactionTime) {}
