package com.jiajunxie.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class FxLegacyRebuildRequest {
  @NotEmpty private List<String> accountIds;
  private List<String> openingBalanceTransactionIds;
  private Integer openingRateLookAheadDays;
}
