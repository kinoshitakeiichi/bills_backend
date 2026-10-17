package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class TransactionPaymentRequest {
  private String paymentTypeCode;
  private String paymentRuleId;

  @Min(value = 1, message = "期数必须大于等于 1")
  private Integer installmentCount;

  @DecimalMin(value = "0", inclusive = true, message = "手续费不能小于零")
  private BigDecimal feeAmount;

  private String remainderRuleCode;

  private List<BigDecimal> manualPrincipalAmounts;
  private List<BigDecimal> manualFeeAmounts;
}
