package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record CreditPurchaseRefundRequestVo(BigDecimal amount, Instant transactionTime, String remark) {}
