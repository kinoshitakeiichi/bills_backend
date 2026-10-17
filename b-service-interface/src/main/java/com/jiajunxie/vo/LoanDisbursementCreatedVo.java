package com.jiajunxie.vo;

import java.math.BigDecimal;

public record LoanDisbursementCreatedVo(
    String transactionId,
    String loanAccountId,
    String targetAccountId,
    BigDecimal amount,
    String currency) {}
