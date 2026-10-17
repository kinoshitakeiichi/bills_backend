package com.jiajunxie.domain.budget;

import java.util.Locale;

public final class BudgetDimensionSourceContract {

  /**
   * Returns the transaction whose analysis dimensions should be used for Budget matching.
   *
   * <p>Refunds belong to the original purchase/expense dimensions; other transactions use their own
   * dimensions.
   */
  public String dimensionSourceTransactionId(
      String transactionId, String transactionType, String originReferenceId) {
    String type = normalize(transactionType);
    if ("CREDIT_REFUND".equals(type)) {
      if (originReferenceId == null || originReferenceId.isBlank()) {
        throw new IllegalStateException("信用卡退款缺少 originReferenceId，无法确定预算冲减维度");
      }
      return originReferenceId.trim();
    }
    if ("REFUND".equals(type) && originReferenceId != null && !originReferenceId.isBlank()) {
      return originReferenceId.trim();
    }
    return requireTransactionId(transactionId);
  }


  private String requireTransactionId(String transactionId) {
    if (transactionId == null || transactionId.isBlank()) {
      throw new IllegalArgumentException("transactionId is required");
    }
    return transactionId.trim();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
