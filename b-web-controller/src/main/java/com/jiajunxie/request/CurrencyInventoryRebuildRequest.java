package com.jiajunxie.request;

import java.util.List;
import lombok.Data;

@Data
public class CurrencyInventoryRebuildRequest {
  /**
   * Optional preview scope. FX/TRANSFER dependency accounts may still be replayed internally, but
   * unrelated accounts must not pollute globalIssues/applyEligible for this scoped request.
   */
  private List<String> accountIds;
}
