package com.jiajunxie.vo;

import java.math.BigDecimal;

public record LoanPaymentCreatedVo(
    String transactionId,
    String loanAccountId,
    String sourceAccountId,
    BigDecimal principalAmount,
    BigDecimal interestAmount,
    BigDecimal feeAmount,
    BigDecimal totalPaymentAmount,
    String currency) {}
