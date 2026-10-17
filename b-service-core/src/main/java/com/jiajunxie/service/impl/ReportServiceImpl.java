package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.transaction.TransactionLedgerPage;
import com.jiajunxie.domain.transaction.TransactionLedgerQueryCriteria;
import com.jiajunxie.domain.transaction.TransactionLedgerRow;
import com.jiajunxie.domain.transaction.TransactionLedgerSort;
import com.jiajunxie.domain.transaction.TransactionNature;
import com.jiajunxie.entity.Category;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.service.ReportService;
import com.jiajunxie.service.TransactionLedgerQueryService;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import com.jiajunxie.vo.ReportCategoryNodeVo;
import com.jiajunxie.vo.ReportOverviewVo;
import com.jiajunxie.vo.ReportQueryVo;
import com.jiajunxie.vo.ReportRateSnapshotVo;
import com.jiajunxie.vo.ReportTrendPointVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

  private static final int PAGE_SIZE = 200;
  private static final int MONEY_SCALE = 4;
  private static final Instant REPORT_HISTORY_START =
      LocalDate.of(1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

  private final TransactionLedgerQueryService ledgerQueryService;
  private final ExchangeRateService exchangeRateService;
  private final CategoryMapper categoryMapper;

  @Override
  @Transactional(readOnly = true)
  public ReportOverviewVo overview(ReportQueryVo query) {
    validate(query);

    String baseCurrency = normalizeCurrency(query.baseCurrency());
    String granularity = normalizeGranularity(query.granularity());
    ZoneId queryZone = query.timezone();
    Instant valuedAt = Instant.now();

    List<Category> categories =
        categoryMapper.selectList(
            Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getCategoryName));
    Map<String, Category> categoryById =
        categories.stream()
            .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));

    Set<String> allowedCategories = expandCategorySelection(query.categoryIds(), categories);
    Instant periodStart = query.dateFrom().atStartOfDay(queryZone).toInstant();
    Instant periodEnd = query.dateTo().plusDays(1).atStartOfDay(queryZone).toInstant();
    List<TransactionLedgerRow> rows =
        loadRows(query, allowedCategories, periodStart, periodEnd);
    List<TransactionLedgerRow> historicalRows =
        loadRows(query, allowedCategories, REPORT_HISTORY_START, periodStart);

    ReportRateBook rateBook = new ReportRateBook(baseCurrency, exchangeRateService);
    BigDecimal openingCumulative = calculateNetSurplus(historicalRows, rateBook);

    Map<String, PeriodAccumulator> periodMap =
        createPeriods(query.dateFrom(), query.dateTo(), granularity);

    BigDecimal totalIncome = BigDecimal.ZERO;
    BigDecimal totalExpense = BigDecimal.ZERO;
    Map<String, BigDecimal> directExpenseByCategory = new HashMap<>();

    for (TransactionLedgerRow row : rows) {
      TransactionNature nature = row.transactionNature();
      if (nature != TransactionNature.INCOME
          && nature != TransactionNature.EXPENSE
          && nature != TransactionNature.REFUND) {
        continue;
      }

      LocalDate date = row.transactionTime().atZone(queryZone).toLocalDate();
      BigDecimal converted = rateBook.convert(row.displayAmount(), row.currency());
      if (converted == null) continue;

      String periodKey = periodKey(date, granularity);
      PeriodAccumulator period = periodMap.get(periodKey);
      if (period == null) continue;

      if (row.transactionNature() == TransactionNature.INCOME) {
        period.income = period.income.add(converted);
        totalIncome = totalIncome.add(converted);
      } else if (row.transactionNature() == TransactionNature.EXPENSE) {
        period.expense = period.expense.add(converted);
        totalExpense = totalExpense.add(converted);
        if (StringUtils.hasText(row.categoryId())) {
          directExpenseByCategory.merge(row.categoryId(), converted, BigDecimal::add);
        }
      } else if (row.transactionNature() == TransactionNature.REFUND) {
        period.expense = period.expense.subtract(converted);
        totalExpense = totalExpense.subtract(converted);
        if (StringUtils.hasText(row.categoryId())) {
          directExpenseByCategory.merge(row.categoryId(), converted.negate(), BigDecimal::add);
        }
      }
    }

    BigDecimal cumulative = openingCumulative;
    List<ReportTrendPointVo> trend = new ArrayList<>();
    for (PeriodAccumulator period : periodMap.values()) {
      BigDecimal net = period.income.subtract(period.expense);
      cumulative = cumulative.add(net);
      trend.add(
          new ReportTrendPointVo(
              period.key,
              period.start,
              period.end,
              money(period.income),
              money(period.expense),
              money(net),
              money(cumulative),
              baseCurrency));
    }

    Map<String, BigDecimal> rolledExpense =
        rollUpCategoryAmounts(directExpenseByCategory, categoryById);
    List<ReportCategoryNodeVo> expenseCategories =
        buildExpenseTree(categories, rolledExpense, totalExpense, baseCurrency);

    return new ReportOverviewVo(
        baseCurrency,
        "REALTIME_CONVERTED",
        valuedAt,
        money(totalIncome),
        money(totalExpense),
        money(totalIncome.subtract(totalExpense)),
        money(cumulative),
        trend,
        expenseCategories,
        rateBook.snapshots());
  }

  private BigDecimal calculateNetSurplus(
      List<TransactionLedgerRow> rows, ReportRateBook rateBook) {
    BigDecimal net = BigDecimal.ZERO;
    for (TransactionLedgerRow row : rows) {
      TransactionNature nature = row.transactionNature();
      if (nature != TransactionNature.INCOME
          && nature != TransactionNature.EXPENSE
          && nature != TransactionNature.REFUND) {
        continue;
      }

      BigDecimal converted = rateBook.convert(row.displayAmount(), row.currency());
      if (converted == null) continue;

      if (nature == TransactionNature.INCOME || nature == TransactionNature.REFUND) {
        net = net.add(converted);
      } else {
        net = net.subtract(converted);
      }
    }
    return net;
  }

  private List<TransactionLedgerRow> loadRows(
      ReportQueryVo query,
      Set<String> allowedCategories,
      Instant startInclusive,
      Instant endExclusive) {
    List<TransactionLedgerRow> result = new ArrayList<>();
    int page = 1;
    int totalPages;

    do {
      TransactionLedgerPage response =
          ledgerQueryService.query(
              new TransactionLedgerQueryCriteria(
                  startInclusive,
                  endExclusive,
                  null,
                  false,
                  null,
                  null,
                  null,
                  query.personIds(),
                  false,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  page,
                  PAGE_SIZE,
                  TransactionLedgerSort.TIME_ASC));

      for (TransactionLedgerRow row : response.items()) {
        if (!matchesAccounts(row, query.accountIds())) continue;
        if (!allowedCategories.isEmpty()
            && (!StringUtils.hasText(row.categoryId())
                || !allowedCategories.contains(row.categoryId()))) {
          continue;
        }
        result.add(row);
      }

      totalPages = response.totalPages();
      page++;
    } while (page <= totalPages);

    return result;
  }

  private boolean matchesAccounts(TransactionLedgerRow row, List<String> accountIds) {
    if (accountIds == null || accountIds.isEmpty()) return true;
    Set<String> selected = new HashSet<>(accountIds);
    return row.accountSummaries().stream()
        .map(summary -> summary.accountId())
        .anyMatch(selected::contains);
  }

  private Set<String> expandCategorySelection(
      List<String> selectedIds, List<Category> categories) {
    if (selectedIds == null || selectedIds.isEmpty()) return Set.of();

    Map<String, List<String>> children = new HashMap<>();
    for (Category category : categories) {
      if (StringUtils.hasText(category.getParentId())) {
        children.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
            .add(category.getCategoryId());
      }
    }

    Set<String> result = new HashSet<>();
    for (String selected : selectedIds) {
      if (!StringUtils.hasText(selected)) continue;
      collectDescendants(selected, children, result);
    }
    return result;
  }

  private void collectDescendants(
      String categoryId, Map<String, List<String>> children, Set<String> result) {
    if (!result.add(categoryId)) return;
    for (String child : children.getOrDefault(categoryId, List.of())) {
      collectDescendants(child, children, result);
    }
  }

  private Map<String, PeriodAccumulator> createPeriods(
      LocalDate from, LocalDate to, String granularity) {
    Map<String, PeriodAccumulator> result = new LinkedHashMap<>();
    if ("MONTH".equals(granularity)) {
      YearMonth month = YearMonth.from(from);
      YearMonth end = YearMonth.from(to);
      while (!month.isAfter(end)) {
        LocalDate start = month.atDay(1).isBefore(from) ? from : month.atDay(1);
        LocalDate finish = month.atEndOfMonth().isAfter(to) ? to : month.atEndOfMonth();
        String key = month.toString();
        result.put(key, new PeriodAccumulator(key, start, finish));
        month = month.plusMonths(1);
      }
    } else {
      LocalDate current = from;
      while (!current.isAfter(to)) {
        String key = current.toString();
        result.put(key, new PeriodAccumulator(key, current, current));
        current = current.plusDays(1);
      }
    }
    return result;
  }

  private String periodKey(LocalDate date, String granularity) {
    return "MONTH".equals(granularity) ? YearMonth.from(date).toString() : date.toString();
  }

  private Map<String, BigDecimal> rollUpCategoryAmounts(
      Map<String, BigDecimal> direct, Map<String, Category> categoryById) {
    Map<String, BigDecimal> result = new HashMap<>(direct);
    for (Map.Entry<String, BigDecimal> entry : direct.entrySet()) {
      Category category = categoryById.get(entry.getKey());
      Set<String> visited = new HashSet<>();
      while (category != null
          && StringUtils.hasText(category.getParentId())
          && visited.add(category.getCategoryId())) {
        String parentId = category.getParentId();
        result.merge(parentId, entry.getValue(), BigDecimal::add);
        category = categoryById.get(parentId);
      }
    }
    return result;
  }

  private List<ReportCategoryNodeVo> buildExpenseTree(
      List<Category> categories,
      Map<String, BigDecimal> amounts,
      BigDecimal totalExpense,
      String currency) {
    Map<String, List<Category>> children = new HashMap<>();
    for (Category category : categories) {
      if (StringUtils.hasText(category.getParentId())) {
        children.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>()).add(category);
      }
    }

    Comparator<Category> order =
        Comparator.<Category, BigDecimal>comparing(
                category -> amounts.getOrDefault(category.getCategoryId(), BigDecimal.ZERO),
                Comparator.reverseOrder())
            .thenComparing(Category::getCategoryName);

    return categories.stream()
        .filter(category -> !StringUtils.hasText(category.getParentId()))
        .filter(category -> "EXPENSE".equalsIgnoreCase(category.getCategoryTypeCode()))
        .sorted(order)
        .map(category -> categoryNode(category, children, amounts, totalExpense, currency, order))
        .filter(node -> node.amount().compareTo(BigDecimal.ZERO) != 0)
        .toList();
  }

  private ReportCategoryNodeVo categoryNode(
      Category category,
      Map<String, List<Category>> children,
      Map<String, BigDecimal> amounts,
      BigDecimal totalExpense,
      String currency,
      Comparator<Category> order) {
    BigDecimal amount = amounts.getOrDefault(category.getCategoryId(), BigDecimal.ZERO);
    BigDecimal share =
        totalExpense.signum() == 0
            ? BigDecimal.ZERO
            : amount.divide(totalExpense, 6, RoundingMode.HALF_UP);

    List<ReportCategoryNodeVo> childNodes =
        children.getOrDefault(category.getCategoryId(), List.of()).stream()
            .sorted(order)
            .map(child -> categoryNode(child, children, amounts, totalExpense, currency, order))
            .filter(node -> node.amount().compareTo(BigDecimal.ZERO) != 0)
            .toList();

    return new ReportCategoryNodeVo(
        category.getCategoryId(),
        category.getCategoryName(),
        category.getCategoryTypeCode(),
        money(amount),
        share,
        currency,
        childNodes);
  }

  private void validate(ReportQueryVo query) {
    if (query == null || query.dateFrom() == null || query.dateTo() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "报表必须指定日期范围");
    }
    if (query.dateFrom().isAfter(query.dateTo())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "报表开始日期不能晚于结束日期");
    }
    if (query.dateFrom().plusYears(5).isBefore(query.dateTo())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "单次报表区间不能超过 5 年");
    }
    normalizeCurrency(query.baseCurrency());
    normalizeGranularity(query.granularity());
  }

  private String normalizeCurrency(String value) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "报表基准币种不能为空");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeGranularity(String value) {
    String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "MONTH";
    if (!List.of("DAY", "MONTH").contains(normalized)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "报表粒度仅支持 DAY 或 MONTH");
    }
    return normalized;
  }

  private BigDecimal money(BigDecimal value) {
    BigDecimal normalized = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
  }

  private static final class PeriodAccumulator {
    private final String key;
    private final LocalDate start;
    private final LocalDate end;
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expense = BigDecimal.ZERO;

    private PeriodAccumulator(String key, LocalDate start, LocalDate end) {
      this.key = key;
      this.start = start;
      this.end = end;
    }
  }

  private static final class ReportRateBook {
    private final String baseCurrency;
    private final ExchangeRateService exchangeRateService;
    private final Map<String, RealtimeExchangeRateVo> rates = new LinkedHashMap<>();

    private ReportRateBook(String baseCurrency, ExchangeRateService exchangeRateService) {
      this.baseCurrency = baseCurrency;
      this.exchangeRateService = exchangeRateService;
    }

    private BigDecimal convert(BigDecimal amount, String currency) {
      if (amount == null) return null;
      if (!StringUtils.hasText(currency) || baseCurrency.equalsIgnoreCase(currency)) return amount;

      String sourceCurrency = currency.trim().toUpperCase(Locale.ROOT);
      RealtimeExchangeRateVo rate =
          rates.computeIfAbsent(
              sourceCurrency,
              ignored -> exchangeRateService.realtime(sourceCurrency, baseCurrency));
      return amount.multiply(rate.exchangeRate());
    }

    private List<ReportRateSnapshotVo> snapshots() {
      return rates.values().stream()
          .map(
              rate ->
                  new ReportRateSnapshotVo(
                      rate.baseCurrency(),
                      rate.quoteCurrency(),
                      rate.exchangeRate(),
                      rate.rateSourceCode(),
                      rate.observedAt(),
                      rate.derived()))
          .toList();
    }
  }
}
