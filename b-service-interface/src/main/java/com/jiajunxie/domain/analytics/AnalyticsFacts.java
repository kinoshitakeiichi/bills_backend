package com.jiajunxie.domain.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public record AnalyticsFacts(
    String transactionId,
    String transactionType,
    String originCode,
    String originReferenceId,
    Instant transactionTime,
    BigDecimal eligibleAmount,
    String currency,
    String categoryId,
    String merchant,
    Set<String> accountIds,
    Map<String, BigDecimal> personAllocatedAmounts) {

  public AnalyticsFacts {
    accountIds = accountIds == null ? Collections.emptySet() : Set.copyOf(accountIds);
    personAllocatedAmounts =
        personAllocatedAmounts == null
            ? Collections.emptyMap()
            : Map.copyOf(personAllocatedAmounts);
  }
}
