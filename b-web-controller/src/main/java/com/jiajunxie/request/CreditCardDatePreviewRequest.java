package com.jiajunxie.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class CreditCardDatePreviewRequest {
  @NotNull(message = "参考日期不能为空") private LocalDate referenceDate;
  @NotNull(message = "Profile 草稿不能为空") @Valid private CreditCardProfileRequest profile;
  @Valid private List<CreditPaymentRuleRequest> rules;
}
