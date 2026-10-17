package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.domain.budget.BudgetActualFacts;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.Facts;
import com.jiajunxie.entity.Budget;
import com.jiajunxie.entity.BudgetItem;
import com.jiajunxie.entity.BudgetTarget;
import com.jiajunxie.entity.Category;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.BudgetItemMapper;
import com.jiajunxie.mapper.BudgetMapper;
import com.jiajunxie.mapper.BudgetTargetMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.BudgetSaveVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BudgetServiceImplTest {

  @Test
  void createPersistsBudgetItemsAndTargetsAsOneAggregate() {
    Fixture f = fixture();
    Category food = new Category();
    food.setCategoryId("food");
    when(f.categoryMapper.selectById("food")).thenReturn(food);
    when(f.itemMapper.selectByBudgetId(any())).thenReturn(List.of());

    BudgetSaveVo request =
        new BudgetSaveVo(
            "Monthly Food",
            "monthly",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            "jpy",
            true,
            List.of(
                new BudgetSaveVo.Item(
                    new BigDecimal("50000"),
                    "jpy",
                    false,
                    10,
                    List.of(
                        new BudgetSaveVo.Target(
                            "category", "food", null, true)))));

    String budgetId = f.service.createBudget(request);

    ArgumentCaptor<Budget> budgetCaptor = ArgumentCaptor.forClass(Budget.class);
    verify(f.budgetMapper).insert(budgetCaptor.capture());
    assertEquals(budgetId, budgetCaptor.getValue().getBudgetId());
    assertEquals("MONTHLY", budgetCaptor.getValue().getPeriodTypeCode());
    assertEquals("JPY", budgetCaptor.getValue().getBaseCurrency());

    ArgumentCaptor<BudgetItem> itemCaptor = ArgumentCaptor.forClass(BudgetItem.class);
    verify(f.itemMapper).insert(itemCaptor.capture());
    assertEquals(budgetId, itemCaptor.getValue().getBudgetId());
    assertEquals("JPY", itemCaptor.getValue().getCurrency());

    ArgumentCaptor<BudgetTarget> targetCaptor = ArgumentCaptor.forClass(BudgetTarget.class);
    verify(f.targetMapper).insert(targetCaptor.capture());
    assertEquals("CATEGORY", targetCaptor.getValue().getTargetTypeCode());
    assertEquals("food", targetCaptor.getValue().getTargetId());
    assertEquals(true, targetCaptor.getValue().getIncludeDescendants());
  }

  @Test
  void periodActualRunsFactsResolverThenAggregation() {
    Fixture f = fixture();

    Budget budget = new Budget();
    budget.setBudgetId("budget");
    budget.setBudgetName("Food");
    budget.setPeriodTypeCode("MONTHLY");
    budget.setPeriodStart(LocalDate.of(2026, 8, 1));
    budget.setPeriodEnd(LocalDate.of(2026, 8, 31));
    budget.setBaseCurrency("JPY");
    budget.setEnabled(true);
    when(f.budgetMapper.selectById("budget")).thenReturn(budget);

    BudgetItem item = new BudgetItem();
    item.setBudgetItemId("item");
    item.setBudgetId("budget");
    item.setBudgetAmount(new BigDecimal("1000"));
    item.setCurrency("JPY");
    item.setRolloverEnabled(false);
    item.setSortOrder(10);
    when(f.itemMapper.selectByBudgetId("budget")).thenReturn(List.of(item));

    BudgetTarget target = new BudgetTarget();
    target.setBudgetTargetId("target");
    target.setBudgetItemId("item");
    target.setTargetTypeCode("CATEGORY");
    target.setTargetId("food");
    target.setIncludeDescendants(true);
    when(f.targetMapper.selectByBudgetItemId("item")).thenReturn(List.of(target));

    when(f.transactionMapper.selectBudgetCandidateIds(any(), any()))
        .thenReturn(List.of("expense", "refund", "loan-purchase"));
    when(f.factsResolver.resolve("expense"))
        .thenReturn(facts("expense", "EXPENSE", "100", "JPY"));
    when(f.factsResolver.resolve("refund"))
        .thenReturn(facts("refund", "CREDIT_REFUND", "30", "JPY"));
    when(f.factsResolver.resolve("loan-purchase"))
        .thenReturn(facts("loan-purchase", "LOAN_PURCHASE", "50", "JPY"));

    var result = f.service.getActual("budget", ZoneId.of("Asia/Tokyo"));

    assertEquals(1, result.items().size());
    assertEquals(new BigDecimal("120"), result.items().get(0).actualAmount());
    assertEquals(new BigDecimal("880"), result.items().get(0).remainingAmount());
    verify(f.factsResolver).resolve("expense");
    verify(f.factsResolver).resolve("refund");
    verify(f.factsResolver).resolve("loan-purchase");
  }


  @Test
  void periodActualConvertsForeignCurrencyByTransactionDate() {
    Fixture f = fixture();

    Budget budget = new Budget();
    budget.setBudgetId("budget");
    budget.setBudgetName("Income");
    budget.setPeriodTypeCode("MONTHLY");
    budget.setPeriodStart(LocalDate.of(2026, 7, 1));
    budget.setPeriodEnd(LocalDate.of(2026, 7, 31));
    budget.setBaseCurrency("JPY");
    budget.setEnabled(true);
    when(f.budgetMapper.selectById("budget")).thenReturn(budget);

    BudgetItem item = new BudgetItem();
    item.setBudgetItemId("item");
    item.setBudgetId("budget");
    item.setBudgetAmount(new BigDecimal("100000"));
    item.setCurrency("JPY");
    item.setRolloverEnabled(false);
    item.setSortOrder(10);
    when(f.itemMapper.selectByBudgetId("budget")).thenReturn(List.of(item));
    when(f.targetMapper.selectByBudgetItemId("item")).thenReturn(List.of());

    when(f.transactionMapper.selectBudgetCandidateIds(any(), any()))
        .thenReturn(List.of("income-usd"));
    when(f.factsResolver.resolve("income-usd"))
        .thenReturn(
            new BudgetActualFacts(
                "income-usd",
                "INCOME",
                null,
                null,
                Instant.parse("2026-07-15T03:00:00Z"),
                new BigDecimal("100"),
                "USD",
                new Facts("income", Set.of(), Set.of("usd-account"), "Employer", Map.of())));

    when(f.exchangeRateService.effective(
            LocalDate.of(2026, 7, 15), "USD", "JPY", null))
        .thenReturn(
            new ExchangeRateVo(
                "rate",
                LocalDate.of(2026, 7, 15),
                "USD",
                "JPY",
                new BigDecimal("150"),
                "MANUAL",
                Instant.parse("2026-07-15T00:00:00Z"),
                false));

    var result = f.service.getActual("budget", ZoneId.of("Asia/Tokyo"));

    assertEquals(new BigDecimal("15000.0000"), result.items().get(0).actualAmount());
    verify(f.exchangeRateService)
        .effective(LocalDate.of(2026, 7, 15), "USD", "JPY", null);
  }

  @Test
  void updateReplacesOldChildrenInsideAggregate() {
    Fixture f = fixture();

    Budget existing = new Budget();
    existing.setBudgetId("budget");
    existing.setBudgetName("Old");
    existing.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(f.budgetMapper.selectById("budget")).thenReturn(existing);
    when(f.budgetMapper.updateById(any(Budget.class))).thenReturn(1);

    BudgetItem oldItem = new BudgetItem();
    oldItem.setBudgetItemId("old-item");
    when(f.itemMapper.selectByBudgetId("budget"))
        .thenReturn(List.of(oldItem));

    BudgetSaveVo request =
        new BudgetSaveVo(
            "New",
            "YEARLY",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            "JPY",
            true,
            List.of(
                new BudgetSaveVo.Item(
                    BigDecimal.ZERO, "JPY", false, 0, List.of())));

    assertEquals(1, f.service.updateBudget("budget", request));

    verify(f.targetMapper).deleteByBudgetItemId("old-item");
    verify(f.itemMapper).deleteByBudgetId("budget");
    verify(f.itemMapper).insert(any(BudgetItem.class));
  }

  @Test
  void childCategoryBudgetsCannotExceedParentBudget() {
    Fixture f = fixture();

    Category expense = new Category();
    expense.setCategoryId("expense");
    expense.setParentId(null);
    Category travel = new Category();
    travel.setCategoryId("travel");
    travel.setParentId("expense");
    when(f.categoryMapper.selectById("expense")).thenReturn(expense);
    when(f.categoryMapper.selectById("travel")).thenReturn(travel);

    BudgetSaveVo request =
        new BudgetSaveVo(
            "July",
            "MONTHLY",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "JPY",
            true,
            List.of(
                new BudgetSaveVo.Item(
                    new BigDecimal("500"),
                    "JPY",
                    false,
                    0,
                    List.of(new BudgetSaveVo.Target("CATEGORY", "expense", null, true))),
                new BudgetSaveVo.Item(
                    new BigDecimal("600"),
                    "JPY",
                    false,
                    10,
                    List.of(new BudgetSaveVo.Target("CATEGORY", "travel", null, true)))));

    assertThrows(RuntimeException.class, () -> f.service.createBudget(request));
    verify(f.budgetMapper, times(0)).insert(any(Budget.class));
  }

  @Test
  void missingReferenceTargetIsRejectedBeforePersistence() {
    Fixture f = fixture();
    when(f.categoryMapper.selectById("missing")).thenReturn(null);

    BudgetSaveVo request =
        new BudgetSaveVo(
            "Budget",
            "MONTHLY",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            "JPY",
            true,
            List.of(
                new BudgetSaveVo.Item(
                    new BigDecimal("100"),
                    "JPY",
                    false,
                    0,
                    List.of(
                        new BudgetSaveVo.Target(
                            "CATEGORY", "missing", null, false)))));

    assertThrows(RuntimeException.class, () -> f.service.createBudget(request));
    verify(f.budgetMapper, times(0)).insert(any(Budget.class));
  }

  private BudgetActualFacts facts(String id, String type, String amount, String currency) {
    return new BudgetActualFacts(
        id,
        type,
        "CREDIT_REFUND".equals(type) ? "CREDIT_REFUND" : null,
        "CREDIT_REFUND".equals(type) ? "purchase" : null,
        Instant.parse("2026-08-12T00:00:00Z"),
        new BigDecimal(amount),
        currency,
        new Facts(
            "food",
            Set.of("root"),
            Set.of("card"),
            "Cafe",
            Map.of()));
  }

  private Fixture fixture() {
    BudgetMapper budgetMapper = mock(BudgetMapper.class);
    BudgetItemMapper itemMapper = mock(BudgetItemMapper.class);
    BudgetTargetMapper targetMapper = mock(BudgetTargetMapper.class);
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    BudgetActualFactsResolver factsResolver = mock(BudgetActualFactsResolver.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    PersonMapper personMapper = mock(PersonMapper.class);
    ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);

    BudgetServiceImpl service =
        new BudgetServiceImpl(
            budgetMapper,
            itemMapper,
            targetMapper,
            transactionMapper,
            factsResolver,
            categoryMapper,
            accountMapper,
            personMapper,
            exchangeRateService);

    return new Fixture(
        service,
        budgetMapper,
        itemMapper,
        targetMapper,
        transactionMapper,
        factsResolver,
        categoryMapper,
        exchangeRateService);
  }

  private record Fixture(
      BudgetServiceImpl service,
      BudgetMapper budgetMapper,
      BudgetItemMapper itemMapper,
      BudgetTargetMapper targetMapper,
      TransactionMapper transactionMapper,
      BudgetActualFactsResolver factsResolver,
      CategoryMapper categoryMapper,
      ExchangeRateService exchangeRateService) {}
}
