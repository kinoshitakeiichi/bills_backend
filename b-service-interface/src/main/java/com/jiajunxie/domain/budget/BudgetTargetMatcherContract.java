package com.jiajunxie.domain.budget;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BudgetTargetMatcherContract {

  public enum TargetType {
    CATEGORY,
    ACCOUNT,
    PERSON,
    MERCHANT
  }

  public record Target(
      TargetType type,
      String targetId,
      String targetValue,
      boolean includeDescendants) {}

  public record Facts(
      String categoryId,
      Set<String> categoryAncestorIds,
      Set<String> accountIds,
      String merchant,
      Map<String, BigDecimal> personAllocatedAmounts) {

    public Facts {
      categoryAncestorIds =
          categoryAncestorIds == null ? Collections.emptySet() : Set.copyOf(categoryAncestorIds);
      accountIds = accountIds == null ? Collections.emptySet() : Set.copyOf(accountIds);
      personAllocatedAmounts =
          personAllocatedAmounts == null
              ? Collections.emptyMap()
              : Map.copyOf(personAllocatedAmounts);
    }
  }

  /**
   * Returns the eligible actual amount contributed to this target.
   *
   * <p>For CATEGORY / ACCOUNT / MERCHANT this is the transaction-level eligible amount. For PERSON
   * it is the person's authoritative TransactionAllocation.allocatedAmount.
   */
  public BigDecimal matchedAmount(Target target, Facts facts, BigDecimal eligibleTransactionAmount) {
    Objects.requireNonNull(target, "budget target is required");
    Objects.requireNonNull(facts, "budget facts are required");

    BigDecimal eligible =
        eligibleTransactionAmount == null ? BigDecimal.ZERO : eligibleTransactionAmount.abs();

    return switch (target.type()) {
      case CATEGORY -> matchesCategory(target, facts) ? eligible : BigDecimal.ZERO;
      case ACCOUNT -> matchesAccount(target, facts) ? eligible : BigDecimal.ZERO;
      case MERCHANT -> matchesMerchant(target, facts) ? eligible : BigDecimal.ZERO;
      case PERSON -> matchedPersonAmount(target, facts);
    };
  }

  private boolean matchesCategory(Target target, Facts facts) {
    String targetId = normalizeId(target.targetId());
    if (targetId.isEmpty()) return false;
    if (targetId.equals(normalizeId(facts.categoryId()))) return true;
    return target.includeDescendants()
        && facts.categoryAncestorIds().stream()
            .map(this::normalizeId)
            .anyMatch(targetId::equals);
  }

  private boolean matchesAccount(Target target, Facts facts) {
    String targetId = normalizeId(target.targetId());
    if (targetId.isEmpty()) return false;
    return facts.accountIds().stream().map(this::normalizeId).anyMatch(targetId::equals);
  }

  private boolean matchesMerchant(Target target, Facts facts) {
    String targetValue = normalizeText(target.targetValue());
    if (targetValue.isEmpty()) return false;
    return targetValue.equals(normalizeText(facts.merchant()));
  }

  private BigDecimal matchedPersonAmount(Target target, Facts facts) {
    String targetId = normalizeId(target.targetId());
    if (targetId.isEmpty()) return BigDecimal.ZERO;
    return facts.personAllocatedAmounts().entrySet().stream()
        .filter(entry -> targetId.equals(normalizeId(entry.getKey())))
        .map(Map.Entry::getValue)
        .filter(Objects::nonNull)
        .findFirst()
        .map(BigDecimal::abs)
        .orElse(BigDecimal.ZERO);
  }

  private String normalizeId(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
