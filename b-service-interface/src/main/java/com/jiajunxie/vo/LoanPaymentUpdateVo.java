package com.jiajunxie.vo;

import java.time.Instant;

public record LoanPaymentUpdateVo(
    String sourceAccountId,
    String remark,
    Instant transactionTime) {}
