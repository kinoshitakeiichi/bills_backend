package com.jiajunxie.vo;

import java.math.BigDecimal;

public record LoanPurchaseAdoptedVo(
    String transactionId,
    String loanAccountId,
    BigDecimal principalAmount,
    String currency,
    boolean changed) {}
