package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.util.List;

/** Business payment-plan input for a credit-card purchase. */
public record TransactionPaymentVo(
    String paymentTypeCode,
    String paymentRuleId,
    Integer installmentCount,
    BigDecimal feeAmount,
    String remainderRuleCode,
    List<BigDecimal> manualPrincipalAmounts,
    List<BigDecimal> manualFeeAmounts) {}
