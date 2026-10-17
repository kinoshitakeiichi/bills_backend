package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanDisbursementCreateVo(
    String loanAccountId,
    String targetAccountId,
    BigDecimal amount,
    String currency,
    String remark,
    Instant transactionTime) {}
