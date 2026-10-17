package com.jiajunxie.domain.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.Facts;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.Target;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.TargetType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BudgetTargetMatcherContractTest {
  private final BudgetTargetMatcherContract matcher = new BudgetTargetMatcherContract();

  @Test
  void categoryMatchesDirectlyAndOptionallyThroughAncestors() {
    Facts facts = facts("food-child", Set.of("food-parent", "root"), Set.of(), null, Map.of());

    assertEquals(
        new BigDecimal("100"),
        matcher.matchedAmount(
            new Target(TargetType.CATEGORY, "food-child", null, false),
            facts,
            new BigDecimal("100")));

    assertEquals(
        BigDecimal.ZERO,
        matcher.matchedAmount(
            new Target(TargetType.CATEGORY, "food-parent", null, false),
            facts,
            new BigDecimal("100")));

    assertEquals(
        new BigDecimal("100"),
        matcher.matchedAmount(
            new Target(TargetType.CATEGORY, "food-parent", null, true),
            facts,
            new BigDecimal("100")));
  }

  @Test
  void accountMatchesOnlyRealParticipatingAccounts() {
    Facts facts = facts(null, Set.of(), Set.of("cash", "card"), null, Map.of());

    assertEquals(
        new BigDecimal("80"),
        matcher.matchedAmount(
            new Target(TargetType.ACCOUNT, "card", null, false),
            facts,
            new BigDecimal("80")));
    assertEquals(
        BigDecimal.ZERO,
        matcher.matchedAmount(
            new Target(TargetType.ACCOUNT, "bank", null, false),
            facts,
            new BigDecimal("80")));
  }

  @Test
  void personUsesAllocatedAmountInsteadOfWholeTransactionAmount() {
    Facts facts =
        facts(null, Set.of(), Set.of(), null,
            Map.of("person-a", new BigDecimal("60"), "person-b", new BigDecimal("40")));

    assertEquals(
        new BigDecimal("60"),
        matcher.matchedAmount(
            new Target(TargetType.PERSON, "person-a", null, false),
            facts,
            new BigDecimal("100")));
    assertEquals(
        new BigDecimal("40"),
        matcher.matchedAmount(
            new Target(TargetType.PERSON, "person-b", null, false),
            facts,
            new BigDecimal("100")));
  }

  @Test
  void merchantUsesNormalizedExactValue() {
    Facts facts = facts(null, Set.of(), Set.of(), "  MUJI  ", Map.of());

    assertEquals(
        new BigDecimal("50"),
        matcher.matchedAmount(
            new Target(TargetType.MERCHANT, null, "muji", false),
            facts,
            new BigDecimal("50")));
    assertEquals(
        BigDecimal.ZERO,
        matcher.matchedAmount(
            new Target(TargetType.MERCHANT, null, "MUJI Shibuya", false),
            facts,
            new BigDecimal("50")));
  }

  private Facts facts(
      String categoryId,
      Set<String> ancestors,
      Set<String> accounts,
      String merchant,
      Map<String, BigDecimal> persons) {
    return new Facts(categoryId, ancestors, accounts, merchant, persons);
  }
}
