package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.domain.analytics.AnalyticsFacts;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Person;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalyticsServiceImplTest {

  @Test
  void periodQueryUsesTimezoneBoundariesAndReturnsEnrichedReadModel() {
    Fixture f = fixture();

    when(f.transactionMapper.selectAnalyticsCandidateIds(any(), any()))
        .thenReturn(List.of("income", "expense", "refund"));

    when(f.factsResolver.resolve("income"))
        .thenReturn(
            facts(
                "income",
                "INCOME",
                "300",
                "JPY",
                "salary",
                "Employer",
                "bank",
                Map.of("person-a", new BigDecimal("300"))));
    when(f.factsResolver.resolve("expense"))
        .thenReturn(
            facts(
                "expense",
                "EXPENSE",
                "100",
                "JPY",
                "coffee",
                "Cafe",
                "cash",
                Map.of("person-a", new BigDecimal("60"))));
    when(f.factsResolver.resolve("refund"))
        .thenReturn(
            facts(
                "refund",
                "REFUND",
                "30",
                "JPY",
                "coffee",
                "Cafe",
                "cash",
                Map.of("person-a", new BigDecimal("18"))));

    Category salary = category("salary", "工资", null);
    Category coffee = category("coffee", "咖啡", "food");
    Category food = category("food", "餐饮", null);
    when(f.categoryMapper.selectByIds(any())).thenReturn(List.of(salary, coffee));
    when(f.categoryMapper.selectById("food")).thenReturn(food);

    when(f.accountMapper.selectByIds(any()))
        .thenReturn(
            List.of(
                account("bank", "工资账户"),
                account("cash", "现金")));
    when(f.personMapper.selectByIds(any()))
        .thenReturn(List.of(person("person-a", "小谢")));

    var result =
        f.service.getPeriod(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            ZoneId.of("Asia/Tokyo"));

    assertEquals(1, result.currencies().size());
    var jpy = result.currencies().get(0);
    assertEquals("JPY", jpy.currency());
    assertEquals(new BigDecimal("300"), jpy.totals().income());
    assertEquals(new BigDecimal("70"), jpy.totals().expense());
    assertEquals(new BigDecimal("230"), jpy.totals().netSurplus());

    assertEquals(
        "餐饮 / 咖啡",
        jpy.categories().stream()
            .filter(item -> item.key().equals("coffee"))
            .findFirst()
            .orElseThrow()
            .label());
    assertEquals(
        "现金",
        jpy.accounts().stream()
            .filter(item -> item.key().equals("cash"))
            .findFirst()
            .orElseThrow()
            .label());
    assertEquals(
        "小谢",
        jpy.persons().stream()
            .filter(item -> item.key().equals("person-a"))
            .findFirst()
            .orElseThrow()
            .label());

    ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(f.transactionMapper)
        .selectAnalyticsCandidateIds(startCaptor.capture(), endCaptor.capture());

    assertEquals(
        Instant.parse("2026-07-31T15:00:00Z"),
        startCaptor.getValue());
    assertEquals(
        Instant.parse("2026-08-31T15:00:00Z"),
        endCaptor.getValue());
  }

  @Test
  void readModelKeepsCurrenciesSeparatedAndLabelsExplicitMissingBuckets() {
    Fixture f = fixture();

    when(f.transactionMapper.selectAnalyticsCandidateIds(any(), any()))
        .thenReturn(List.of("jpy", "usd"));

    when(f.factsResolver.resolve("jpy"))
        .thenReturn(
            new AnalyticsFacts(
                "jpy",
                "CREDIT_STATEMENT_FEE",
                "STATEMENT_ADJUSTMENT",
                "adjustment",
                Instant.parse("2026-08-12T00:00:00Z"),
                new BigDecimal("100"),
                "JPY",
                null,
                null,
                Set.of("card"),
                Map.of()));
    when(f.factsResolver.resolve("usd"))
        .thenReturn(
            facts(
                "usd",
                "EXPENSE",
                "20",
                "USD",
                "food",
                "Store",
                "usd-cash",
                Map.of()));

    when(f.categoryMapper.selectByIds(any()))
        .thenReturn(List.of(category("food", "餐饮", null)));
    when(f.accountMapper.selectByIds(any()))
        .thenReturn(
            List.of(
                account("card", "信用卡"),
                account("usd-cash", "美元现金")));
    when(f.personMapper.selectByIds(any())).thenReturn(List.of());

    var result =
        f.service.getPeriod(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            ZoneId.of("Asia/Tokyo"));

    assertEquals(List.of("JPY", "USD"), result.currencies().stream().map(v -> v.currency()).toList());

    var jpy = result.currencies().get(0);
    assertEquals(
        "未分类",
        jpy.categories().stream()
            .filter(item -> item.key().equals("__UNCLASSIFIED__"))
            .findFirst()
            .orElseThrow()
            .label());
    assertEquals(
        "未填写商户",
        jpy.merchants().stream()
            .filter(item -> item.key().equals("__UNSPECIFIED_MERCHANT__"))
            .findFirst()
            .orElseThrow()
            .label());
    assertEquals(
        "未分配人员",
        jpy.persons().stream()
            .filter(item -> item.key().equals("__UNALLOCATED_PERSON__"))
            .findFirst()
            .orElseThrow()
            .label());
  }

  @Test
  void historicalDisabledMasterDataStillKeepsReadableLabels() {
    Fixture f = fixture();

    when(f.transactionMapper.selectAnalyticsCandidateIds(any(), any()))
        .thenReturn(List.of("expense"));
    when(f.factsResolver.resolve("expense"))
        .thenReturn(
            facts(
                "expense",
                "EXPENSE",
                "100",
                "JPY",
                "old-category",
                "Old Store",
                "old-account",
                Map.of("old-person", new BigDecimal("100"))));

    Category category = category("old-category", "旧分类", null);
    category.setEnabled(false);
    Account account = account("old-account", "旧账户");
    account.setEnabled(false);
    Person person = person("old-person", "旧人员");
    person.setEnabled(false);

    when(f.categoryMapper.selectByIds(any())).thenReturn(List.of(category));
    when(f.accountMapper.selectByIds(any())).thenReturn(List.of(account));
    when(f.personMapper.selectByIds(any())).thenReturn(List.of(person));

    var jpy =
        f.service
            .getPeriod(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ZoneId.of("Asia/Tokyo"))
            .currencies()
            .get(0);

    assertEquals("旧分类", jpy.categories().get(0).label());
    assertEquals("旧账户", jpy.accounts().get(0).label());
    assertEquals("旧人员", jpy.persons().get(0).label());
  }

  @Test
  void accountBreakdownUsesAccountOptionLabelForDisplayAndTieSorting() {
    Fixture f = fixture();

    when(f.transactionMapper.selectAnalyticsCandidateIds(any(), any()))
        .thenReturn(List.of("bob-expense", "alice-expense"));
    when(f.factsResolver.resolve("bob-expense"))
        .thenReturn(
            facts(
                "bob-expense",
                "EXPENSE",
                "100",
                "JPY",
                "food",
                "Store",
                "account-b",
                Map.of()));
    when(f.factsResolver.resolve("alice-expense"))
        .thenReturn(
            facts(
                "alice-expense",
                "EXPENSE",
                "100",
                "JPY",
                "food",
                "Store",
                "account-a",
                Map.of()));

    when(f.categoryMapper.selectByIds(any()))
        .thenReturn(List.of(category("food", "餐饮", null)));
    when(f.accountMapper.selectByIds(any()))
        .thenReturn(
            List.of(
                account("account-b", "现金", "owner-b", "JPY"),
                account("account-a", "现金", "owner-a", "JPY")));
    when(f.personMapper.selectByIds(any()))
        .thenReturn(List.of(person("owner-b", "Bob"), person("owner-a", "Alice")));

    var accounts =
        f.service
            .getPeriod(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ZoneId.of("Asia/Tokyo"))
            .currencies()
            .get(0)
            .accounts();

    assertEquals(List.of("Alice的现金 (JPY)", "Bob的现金 (JPY)"),
        accounts.stream().map(item -> item.label()).toList());
  }

  @Test
  void invalidPeriodIsRejectedBeforeQuery() {
    Fixture f = fixture();

    assertThrows(
        BusinessException.class,
        () ->
            f.service.getPeriod(
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 8, 1),
                ZoneId.of("Asia/Tokyo")));
  }

  private Fixture fixture() {
    TransactionMapper transactionMapper = mock(TransactionMapper.class);
    AnalyticsFactsResolver factsResolver = mock(AnalyticsFactsResolver.class);
    CategoryMapper categoryMapper = mock(CategoryMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    PersonMapper personMapper = mock(PersonMapper.class);

    return new Fixture(
        new AnalyticsServiceImpl(
            transactionMapper,
            factsResolver,
            categoryMapper,
            accountMapper,
            personMapper),
        transactionMapper,
        factsResolver,
        categoryMapper,
        accountMapper,
        personMapper);
  }

  private AnalyticsFacts facts(
      String id,
      String type,
      String amount,
      String currency,
      String categoryId,
      String merchant,
      String accountId,
      Map<String, BigDecimal> persons) {
    return new AnalyticsFacts(
        id,
        type,
        null,
        null,
        Instant.parse("2026-08-12T00:00:00Z"),
        new BigDecimal(amount),
        currency,
        categoryId,
        merchant,
        Set.of(accountId),
        persons);
  }

  private Category category(String id, String name, String parentId) {
    Category category = new Category();
    category.setCategoryId(id);
    category.setCategoryName(name);
    category.setParentId(parentId);
    return category;
  }

  private Account account(String id, String name) {
    return account(id, name, null, null);
  }

  private Account account(String id, String name, String ownerPersonId, String currency) {
    Account account = new Account();
    account.setAccountId(id);
    account.setAccountName(name);
    account.setOwnerPersonId(ownerPersonId);
    account.setCurrency(currency);
    return account;
  }

  private Person person(String id, String name) {
    Person person = new Person();
    person.setPersonId(id);
    person.setPersonName(name);
    return person;
  }

  private record Fixture(
      AnalyticsServiceImpl service,
      TransactionMapper transactionMapper,
      AnalyticsFactsResolver factsResolver,
      CategoryMapper categoryMapper,
      AccountMapper accountMapper,
      PersonMapper personMapper) {}
}
