package com.jiajunxie.domain.budget;

import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.Target;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.TargetType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BudgetItemActualAggregationContract {
  private final BudgetActualContract actualContract = new BudgetActualContract();
  private final BudgetTargetMatcherContract matcher = new BudgetTargetMatcherContract();

  /**
   * Target combination semantics:
   *
   * <ul>
   *   <li>same target type = OR
   *   <li>different target types = AND
   *   <li>no targets = all eligible transactions
   * </ul>
   *
   * <p>PERSON is also an amount selector. When several PERSON targets match, their authoritative
   * allocated amounts are summed once per distinct person. Other target types are boolean filters
   * and never multiply the transaction amount.
   */
  public BigDecimal matchedEligibleAmount(
      List<Target> targets, BudgetActualFacts facts) {
    if (facts == null) {
      throw new IllegalArgumentException("budget actual facts are required");
    }

    BigDecimal eligible =
        facts.eligibleAmount() == null ? BigDecimal.ZERO : facts.eligibleAmount().abs();
    if (eligible.signum() == 0) return BigDecimal.ZERO;

    List<Target> safeTargets = targets == null ? List.of() : targets;
    if (safeTargets.isEmpty()) return eligible;

    Map<TargetType, List<Target>> grouped = new EnumMap<>(TargetType.class);
    for (Target target : safeTargets) {
      if (target == null || target.type() == null) {
        throw new IllegalArgumentException("budget target type is required");
      }
      grouped.computeIfAbsent(target.type(), ignored -> new ArrayList<>()).add(target);
    }

    BigDecimal personAmount = null;
    for (Map.Entry<TargetType, List<Target>> group : grouped.entrySet()) {
      if (group.getKey() == TargetType.PERSON) {
        personAmount = matchedDistinctPersons(group.getValue(), facts);
        if (personAmount.signum() == 0) return BigDecimal.ZERO;
        continue;
      }

      boolean anyMatched =
          group.getValue().stream()
              .anyMatch(
                  target ->
                      matcher
                              .matchedAmount(target, facts.targetFacts(), eligible)
                              .signum()
                          > 0);
      if (!anyMatched) return BigDecimal.ZERO;
    }

    if (personAmount == null) return eligible;
    // Defensive cap: validated TransactionAllocation rows should already sum to the transaction
    // amount, but Budget aggregation must never amplify a transaction even if legacy data is bad.
    return personAmount.min(eligible);
  }

  public BigDecimal aggregate(
      String budgetItemCurrency,
      List<Target> targets,
      List<BudgetActualFacts> transactions) {
    BigDecimal total = BigDecimal.ZERO;
    if (transactions == null || transactions.isEmpty()) return total;

    Set<String> seenTransactionIds = new HashSet<>();
    for (BudgetActualFacts facts : transactions) {
      if (facts == null) continue;

      String transactionId = normalizeId(facts.transactionId());
      if (transactionId.isEmpty()) {
        throw new IllegalArgumentException("budget fact transactionId is required");
      }
      if (!seenTransactionIds.add(transactionId)) {
        // A query may join one transaction to many rows. Aggregate the transaction once.
        continue;
      }

      BudgetActualContract.ActualEffect effect =
          actualContract.classify(facts.transactionType(), facts.originCode());
      if (effect == BudgetActualContract.ActualEffect.EXCLUDED) continue;

      BigDecimal matched = matchedEligibleAmount(targets, facts);
      if (matched.signum() == 0) continue;

      actualContract.assertComparableCurrency(budgetItemCurrency, facts.currency());
      total = total.add(
          actualContract.signedActual(
              facts.transactionType(),
              facts.originCode(),
              matched));
    }
    return total;
  }

  private BigDecimal matchedDistinctPersons(
      List<Target> personTargets, BudgetActualFacts facts) {
    BigDecimal total = BigDecimal.ZERO;
    Set<String> seenPersonIds = new HashSet<>();

    for (Target target : personTargets) {
      String personId = normalizeId(target.targetId());
      if (personId.isEmpty() || !seenPersonIds.add(personId)) continue;
      total =
          total.add(
              matcher.matchedAmount(
                  target,
                  facts.targetFacts(),
                  facts.eligibleAmount()));
    }
    return total;
  }

  private String normalizeId(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
