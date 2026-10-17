package com.jiajunxie.response;

import lombok.Data;

@Data
public class CreditPaymentRuleResponse {
  private String ruleId;
  private String accountId;
  private String ruleTypeCode;
  private String purchaseStartMonthDay;
  private String purchaseEndMonthDay;
  private Integer targetMonth;
  private Integer targetDay;
  private Integer priority;
  private Boolean enabled;
}
