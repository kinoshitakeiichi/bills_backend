package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.analytics.AnalyticsFacts;
import com.jiajunxie.domain.analytics.AnalyticsPeriodAggregationContract;
import com.jiajunxie.domain.analytics.AnalyticsPeriodAggregationContract.CurrencyAggregation;
import com.jiajunxie.domain.analytics.AnalyticsPeriodAggregationContract.MetricTotals;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Person;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.AnalyticsService;
import com.jiajunxie.vo.AnalyticsPeriodVo;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {
  private static final AnalyticsPeriodAggregationContract AGGREGATION =
      new AnalyticsPeriodAggregationContract();

  private final TransactionMapper transactionMapper;
  private final AnalyticsFactsResolver factsResolver;
  private final CategoryMapper categoryMapper;
  private final AccountMapper accountMapper;
  private final PersonMapper personMapper;

  @Override
  public AnalyticsPeriodVo getPeriod(
      LocalDate periodStart,
      LocalDate periodEnd,
      ZoneId timezone) {
    if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR,
          "Analytics 周期起止日期不正确");
    }

    ZoneId queryZone = timezone == null ? ZoneId.systemDefault() : timezone;
    Instant startInclusive = periodStart.atStartOfDay(queryZone).toInstant();
    Instant endExclusive = periodEnd.plusDays(1).atStartOfDay(queryZone).toInstant();

    List<AnalyticsFacts> facts = new ArrayList<>();
    for (String transactionId :
        transactionMapper.selectAnalyticsCandidateIds(startInclusive, endExclusive)) {
      facts.add(factsResolver.resolve(transactionId));
    }

    Map<String, CurrencyAggregation> aggregated = AGGREGATION.aggregate(facts);
    Enrichment enrichment = loadEnrichment(aggregated);

    List<AnalyticsPeriodVo.CurrencyMetrics> currencies =
        aggregated.values().stream()
            .map(value -> toCurrencyMetrics(value, enrichment))
            .sorted(Comparator.comparing(AnalyticsPeriodVo.CurrencyMetrics::currency))
            .toList();

    return new AnalyticsPeriodVo(periodStart, periodEnd, currencies);
  }

  private Enrichment loadEnrichment(Map<String, CurrencyAggregation> aggregated) {
    Set<String> categoryIds = new LinkedHashSet<>();
    Set<String> accountIds = new LinkedHashSet<>();
    Set<String> personIds = new LinkedHashSet<>();

    for (CurrencyAggregation currency : aggregated.values()) {
      currency.byCategory().keySet().stream()
          .filter(this::isRealDimensionKey)
          .forEach(categoryIds::add);
      currency.byAccount().keySet().stream()
          .filter(this::isRealDimensionKey)
          .forEach(accountIds::add);
      currency.byPerson().keySet().stream()
          .filter(this::isRealDimensionKey)
          .forEach(personIds::add);
    }

    Map<String, Category> categories =
        indexCategories(categoryIds.isEmpty() ? List.of() : categoryMapper.selectByIds(categoryIds));
    loadCategoryAncestors(categories);
    Map<String, Account> accounts =
        indexAccounts(accountIds.isEmpty() ? List.of() : accountMapper.selectByIds(accountIds));
    accounts.values().stream()
        .map(Account::getOwnerPersonId)
        .filter(ownerPersonId -> ownerPersonId != null && !ownerPersonId.isBlank())
        .forEach(personIds::add);
    Map<String, Person> persons =
        indexPersons(personIds.isEmpty() ? List.of() : personMapper.selectByIds(personIds));

    return new Enrichment(categories, accounts, persons);
  }

  private AnalyticsPeriodVo.CurrencyMetrics toCurrencyMetrics(
      CurrencyAggregation source,
      Enrichment enrichment) {
    return new AnalyticsPeriodVo.CurrencyMetrics(
        source.currency(),
        toTotals(source.totals()),
        dimensionMetrics(source.byCategory(), key -> categoryLabel(key, enrichment.categories())),
        dimensionMetrics(
            source.byAccount(),
            key -> accountLabel(key, enrichment.accounts(), enrichment.persons())),
        dimensionMetrics(source.byPerson(), key -> personLabel(key, enrichment.persons())),
        dimensionMetrics(source.byMerchant(), this::merchantLabel));
  }

  private List<AnalyticsPeriodVo.DimensionMetric> dimensionMetrics(
      Map<String, MetricTotals> source,
      java.util.function.Function<String, String> labelResolver) {
    return source.entrySet().stream()
        .map(
            entry ->
                new AnalyticsPeriodVo.DimensionMetric(
                    entry.getKey(),
                    labelResolver.apply(entry.getKey()),
                    entry.getValue().income(),
                    entry.getValue().expense(),
                    entry.getValue().netSurplus()))
        .sorted(
            Comparator.comparing(AnalyticsPeriodVo.DimensionMetric::expense)
                .reversed()
                .thenComparing(AnalyticsPeriodVo.DimensionMetric::label))
        .toList();
  }

  private AnalyticsPeriodVo.MetricTotals toTotals(MetricTotals source) {
    return new AnalyticsPeriodVo.MetricTotals(
        source.income(),
        source.expense(),
        source.netSurplus());
  }

  private String categoryLabel(String categoryId, Map<String, Category> categories) {
    if (AnalyticsPeriodAggregationContract.UNCLASSIFIED.equals(categoryId)) {
      return "未分类";
    }
    return buildCategoryPath(categoryId, categories, new LinkedHashSet<>());
  }

  private String buildCategoryPath(
      String categoryId,
      Map<String, Category> categories,
      Set<String> visiting) {
    Category category = categories.get(categoryId);
    if (category == null) return categoryId;
    if (!visiting.add(categoryId)) return category.getCategoryName();

    String parentId = category.getParentId();
    if (parentId == null || parentId.isBlank()) {
      return category.getCategoryName();
    }

    String parent = buildCategoryPath(parentId.trim(), categories, visiting);
    return parent == null || parent.isBlank()
        ? category.getCategoryName()
        : parent + " / " + category.getCategoryName();
  }

  private String accountLabel(
      String accountId,
      Map<String, Account> accounts,
      Map<String, Person> persons) {
    if (AnalyticsPeriodAggregationContract.UNCLASSIFIED.equals(accountId)) {
      return "未识别账户";
    }
    Account account = accounts.get(accountId);
    if (account == null) return accountId;

    Person owner = persons.get(account.getOwnerPersonId());
    if (owner == null || owner.getPersonName() == null || owner.getPersonName().isBlank()) {
      return account.getAccountName();
    }

    String currency = account.getCurrency();
    return currency == null || currency.isBlank()
        ? "%s的%s".formatted(owner.getPersonName(), account.getAccountName())
        : "%s的%s (%s)"
            .formatted(owner.getPersonName(), account.getAccountName(), currency);
  }

  private String personLabel(String personId, Map<String, Person> persons) {
    if (AnalyticsPeriodAggregationContract.UNALLOCATED_PERSON.equals(personId)) {
      return "未分配人员";
    }
    Person person = persons.get(personId);
    return person == null ? personId : person.getPersonName();
  }

  private String merchantLabel(String merchant) {
    if (AnalyticsPeriodAggregationContract.UNSPECIFIED_MERCHANT.equals(merchant)) {
      return "未填写商户";
    }
    return merchant;
  }

  private boolean isRealDimensionKey(String key) {
    return key != null && !key.startsWith("__");
  }

  private void loadCategoryAncestors(Map<String, Category> categories) {
    List<Category> frontier = new ArrayList<>(categories.values());
    Set<String> visited = new LinkedHashSet<>(categories.keySet());

    for (int index = 0; index < frontier.size(); index++) {
      Category category = frontier.get(index);
      String parentId = category.getParentId();
      if (parentId == null || parentId.isBlank()) continue;
      parentId = parentId.trim();
      if (!visited.add(parentId)) continue;

      Category parent = categoryMapper.selectById(parentId);
      if (parent != null) {
        categories.put(parentId, parent);
        frontier.add(parent);
      }
    }
  }

  private Map<String, Category> indexCategories(List<Category> values) {
    Map<String, Category> result = new HashMap<>();
    for (Category value : values) {
      if (value != null && value.getCategoryId() != null) {
        result.put(value.getCategoryId(), value);
      }
    }
    return result;
  }

  private Map<String, Account> indexAccounts(List<Account> values) {
    Map<String, Account> result = new HashMap<>();
    for (Account value : values) {
      if (value != null && value.getAccountId() != null) {
        result.put(value.getAccountId(), value);
      }
    }
    return result;
  }

  private Map<String, Person> indexPersons(List<Person> values) {
    Map<String, Person> result = new HashMap<>();
    for (Person value : values) {
      if (value != null && value.getPersonId() != null) {
        result.put(value.getPersonId(), value);
      }
    }
    return result;
  }

  private record Enrichment(
      Map<String, Category> categories,
      Map<String, Account> accounts,
      Map<String, Person> persons) {}
}
