package com.jiajunxie.vo;

import java.math.BigDecimal;

public record LoanPurchaseCreatedVo(
    String transactionId, String loanAccountId, BigDecimal amount, String currency) {}
