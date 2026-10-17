package com.jiajunxie.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.request.BudgetRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BudgetResponseContractTest {

  @Test
  void actualResponseExposesItemLevelCurrencyWithoutFakeGrandTotal() {
    Set<String> rootFields =
        Arrays.stream(BudgetActualResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(rootFields.contains("baseCurrency"));
    assertTrue(rootFields.contains("items"));
    assertTrue(!rootFields.contains("actualAmount"));
    assertTrue(!rootFields.contains("remainingAmount"));

    Set<String> itemFields =
        Arrays.stream(BudgetActualResponse.ItemActualResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

    assertTrue(itemFields.contains("actualAmount"));
    assertTrue(itemFields.contains("remainingAmount"));
    assertTrue(itemFields.contains("currency"));
  }

  @Test
  void requestRuntimeValidationCascadesIntoNestedItemsAndTargets() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      BudgetRequest request =
          new BudgetRequest(
              "Budget",
              "MONTHLY",
              LocalDate.of(2026, 8, 1),
              LocalDate.of(2026, 8, 31),
              "JPY",
              true,
              List.of(
                  new BudgetRequest.Item(
                      new BigDecimal("-1"),
                      "",
                      false,
                      0,
                      List.of(
                          new BudgetRequest.Target(
                              "",
                              null,
                              null,
                              false)))));

      Set<String> violationPaths =
          validator.validate(request).stream()
              .map(violation -> violation.getPropertyPath().toString())
              .collect(Collectors.toSet());

      assertTrue(violationPaths.stream().anyMatch(path -> path.contains("items[0].budgetAmount")));
      assertTrue(violationPaths.stream().anyMatch(path -> path.contains("items[0].currency")));
      assertTrue(
          violationPaths.stream()
              .anyMatch(path -> path.contains("items[0].targets[0].targetTypeCode")));
    }
  }

  @Test
  void requestRejectsEmptyItemsAtRuntime() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      BudgetRequest request =
          new BudgetRequest(
              "Budget",
              "MONTHLY",
              LocalDate.of(2026, 8, 1),
              LocalDate.of(2026, 8, 31),
              "JPY",
              true,
              List.of());

      long itemViolations =
          validator.validate(request).stream()
              .filter(violation -> "items".equals(violation.getPropertyPath().toString()))
              .count();

      assertEquals(1L, itemViolations);
    }
  }
}
