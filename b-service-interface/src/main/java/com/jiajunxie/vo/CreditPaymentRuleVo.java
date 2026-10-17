package com.jiajunxie.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class CreditPaymentRuleVo {
  private String ruleId;
  private String accountId;
  private String ruleTypeCode;
  private String purchaseStartMonthDay;
  private String purchaseEndMonthDay;
  private Integer targetMonth;
  private Integer targetDay;
  private Integer priority;
  private Boolean enabled;
  private Instant createdAt;
  private Instant updatedAt;
}
