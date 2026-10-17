package com.jiajunxie.vo;

import java.math.BigDecimal;

public record StatementPaymentAllocationCommandVo(
    String statementId,
    BigDecimal allocatedAmount,
    Integer allocationOrder) {}
