package com.jiajunxie.service.impl;

import com.jiajunxie.entity.TransactionEntry;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TransactionDisplayAmountResolver {
  public BigDecimal resolve(String transactionTypeCode, List<TransactionEntry> entries) {
    if (entries == null || entries.isEmpty()) return BigDecimal.ZERO;
    String type = normalize(transactionTypeCode);

    if ("TRANSFER".equals(type) || "CREDIT_PAYMENT".equals(type) || "LOAN_PAYMENT".equals(type)
        || "LOAN_DISBURSEMENT".equals(type) || "EXCHANGE".equals(type)) {
      return entries.stream()
          .filter(e -> "SOURCE".equals(normalize(e.getEntryRole())))
          .map(TransactionEntry::getAmount)
          .filter(v -> v != null)
          .map(BigDecimal::abs)
          .findFirst()
          .orElseGet(() -> maxAbsolute(entries));
    }
    return maxAbsolute(entries);
  }

  public String resolveCurrency(String transactionTypeCode, List<TransactionEntry> entries) {
    if (entries == null || entries.isEmpty()) return null;
    String type = normalize(transactionTypeCode);
    if ("TRANSFER".equals(type) || "CREDIT_PAYMENT".equals(type) || "LOAN_PAYMENT".equals(type)
        || "LOAN_DISBURSEMENT".equals(type) || "EXCHANGE".equals(type)) {
      return entries.stream()
          .filter(e -> "SOURCE".equals(normalize(e.getEntryRole())))
          .map(TransactionEntry::getCurrency)
          .findFirst()
          .orElse(entries.get(0).getCurrency());
    }
    return entries.get(0).getCurrency();
  }

  private BigDecimal maxAbsolute(List<TransactionEntry> entries) {
    return entries.stream()
        .map(TransactionEntry::getAmount)
        .filter(v -> v != null)
        .map(BigDecimal::abs)
        .max(Comparator.naturalOrder())
        .orElse(BigDecimal.ZERO);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
