package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record CreditPurchaseRefundRequest(
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
    Instant transactionTime,
    String remark) {}
