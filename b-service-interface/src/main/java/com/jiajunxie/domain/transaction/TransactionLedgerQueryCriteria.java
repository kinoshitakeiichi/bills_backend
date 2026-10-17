package com.jiajunxie.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionLedgerQueryCriteria(
    Instant startInclusive,
    Instant endExclusive,
    String keyword,
    boolean keywordRegex,
    String accountId,
    String accountTypeCode,
    String categoryId,
    List<String> personIds,
    boolean unassignedPerson,
    String transactionTypeCode,
    TransactionNature nature,
    String currency,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    Boolean readonly,
    int page,
    int pageSize,
    TransactionLedgerSort sort) {

  public TransactionLedgerQueryCriteria {
    personIds = personIds == null ? List.of() : List.copyOf(personIds);
    page = page < 1 ? 1 : page;
    pageSize = pageSize < 1 ? 50 : Math.min(pageSize, 200);
    sort = sort == null ? TransactionLedgerSort.TIME_DESC : sort;
  }
}
