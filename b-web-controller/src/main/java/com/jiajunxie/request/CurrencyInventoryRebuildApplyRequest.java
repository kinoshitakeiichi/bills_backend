package com.jiajunxie.request;

import lombok.Data;

@Data
public class CurrencyInventoryRebuildApplyRequest {
  /**
   * Must equal the fingerprint from the full, unscoped Preview that was reviewed immediately
   * before Apply.
   */
  private String expectedFingerprint;

  /** Explicit destructive-operation guard. */
  private String confirmation;
}
