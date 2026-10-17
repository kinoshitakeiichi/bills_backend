package com.jiajunxie.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class LoanPaymentAllocationRequest {
  @NotEmpty
  private List<String> scheduleIds;
}
