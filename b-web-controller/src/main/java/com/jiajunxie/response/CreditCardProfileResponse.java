package com.jiajunxie.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class CreditCardProfileResponse {
  private String accountId;
  private BigDecimal creditLimit;
  private Integer amountScale;
  private Integer billingStartDay;
  private Integer billingEndDay;
  private Integer billingEndMonthOffset;
  private Integer confirmationDay;
  private Integer confirmationMonthOffset;
  private Integer paymentDay;
  private Integer paymentMonthOffset;
  private String invalidDayRuleCode;
  private String businessDayAdjustmentCode;
  private String calendarCode;
  private String timezone;
  private String remainderRuleCode;
  private Boolean enabled;
  private List<CreditPaymentRuleResponse> rules;
}
