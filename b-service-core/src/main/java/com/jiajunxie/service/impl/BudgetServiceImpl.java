package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.budget.BudgetActualFacts;
import com.jiajunxie.domain.budget.BudgetItemActualAggregationContract;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.Target;
import com.jiajunxie.domain.budget.BudgetTargetMatcherContract.TargetType;
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
import com.jiajunxie.service.BudgetService;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.vo.BudgetActualVo;
import com.jiajunxie.vo.BudgetItemVo;
import com.jiajunxie.vo.BudgetSaveVo;
import com.jiajunxie.vo.BudgetTargetVo;
import com.jiajunxie.vo.BudgetVo;
import com.jiajunxie.vo.ExchangeRateVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {
  private static final Set<String> PERIOD_TYPES = Set.of("MONTHLY", "QUARTERLY", "YEARLY");
  private static final BudgetItemActualAggregationContract AGGREGATION =
      new BudgetItemActualAggregationContract();
  private static final int MONEY_SCALE = 4;

  private final BudgetMapper budgetMapper;
  private final BudgetItemMapper budgetItemMapper;
  private final BudgetTargetMapper budgetTargetMapper;
  private final TransactionMapper transactionMapper;
  private final BudgetActualFactsResolver factsResolver;
  private final CategoryMapper categoryMapper;
  private final AccountMapper accountMapper;
  private final PersonMapper personMapper;
  private final ExchangeRateService exchangeRateService;

  @Override
  public List<BudgetVo> listBudgets() {
    return budgetMapper
        .selectList(
            Wrappers.<Budget>lambdaQuery()
                .orderByDesc(Budget::getPeriodStart)
                .orderByAsc(Budget::getBudgetName))
        .stream()
        .map(this::toVo)
        .toList();
  }

  @Override
  public BudgetVo getBudget(String budgetId) {
    return toVo(requireBudget(budgetId));
  }

  @Override
  @Transactional
  public String createBudget(BudgetSaveVo request) {
    validateRequest(request);

    Instant now = Instant.now();
    Budget budget = new Budget();
    budget.setBudgetId(UUID.randomUUID().toString());
    applyBudgetFields(budget, request);
    budget.setCreatedAt(now);
    budget.setUpdatedAt(now);
    budgetMapper.insert(budget);

    replaceChildren(budget.getBudgetId(), request.items());
    return budget.getBudgetId();
  }

  @Override
  @Transactional
  public int updateBudget(String budgetId, BudgetSaveVo request) {
    Budget budget = requireBudget(budgetId);
    validateRequest(request);

    applyBudgetFields(budget, request);
    budget.setUpdatedAt(Instant.now());
    int updated = budgetMapper.updateById(budget);
    replaceChildren(budget.getBudgetId(), request.items());
    return updated;
  }

  @Override
  @Transactional
  public int removeBudget(String budgetId) {
    Budget budget = requireBudget(budgetId);
    List<BudgetItem> items = budgetItemMapper.selectByBudgetId(budget.getBudgetId());
    for (BudgetItem item : items) {
      budgetTargetMapper.deleteByBudgetItemId(item.getBudgetItemId());
    }
    budgetItemMapper.deleteByBudgetId(budget.getBudgetId());
    return budgetMapper.deleteById(budget.getBudgetId());
  }

  @Override
  public BudgetActualVo getActual(String budgetId, ZoneId timezone) {
    Budget budget = requireBudget(budgetId);
    ZoneId queryZone = timezone == null ? ZoneId.systemDefault() : timezone;

    Instant startInclusive = budget.getPeriodStart().atStartOfDay(queryZone).toInstant();
    Instant endExclusive = budget.getPeriodEnd().plusDays(1).atStartOfDay(queryZone).toInstant();

    List<String> candidateIds =
        transactionMapper.selectBudgetCandidateIds(startInclusive, endExclusive);
    List<BudgetActualFacts> facts = new ArrayList<>();
    for (String transactionId : candidateIds) {
      facts.add(factsResolver.resolve(transactionId));
    }

    List<BudgetActualVo.ItemActual> itemActuals = new ArrayList<>();
    for (BudgetItem item : budgetItemMapper.selectByBudgetId(budget.getBudgetId())) {
      List<BudgetTarget> entities =
          budgetTargetMapper.selectByBudgetItemId(item.getBudgetItemId());
      List<Target> targets = entities.stream().map(this::toContractTarget).toList();

      List<BudgetActualFacts> normalizedFacts = normalizeCurrencies(facts, item.getCurrency(), queryZone);
      BigDecimal actual = AGGREGATION.aggregate(item.getCurrency(), targets, normalizedFacts);
      BigDecimal remaining = item.getBudgetAmount().subtract(actual);

      itemActuals.add(
          new BudgetActualVo.ItemActual(
              item.getBudgetItemId(),
              item.getBudgetAmount(),
              actual,
              remaining,
              item.getCurrency(),
              item.getRolloverEnabled(),
              item.getSortOrder(),
              entities.stream().map(this::toTargetVo).toList()));
    }

    return new BudgetActualVo(
        budget.getBudgetId(),
        budget.getBudgetName(),
        budget.getPeriodStart(),
        budget.getPeriodEnd(),
        budget.getBaseCurrency(),
        itemActuals);
  }


  private List<BudgetActualFacts> normalizeCurrencies(
      List<BudgetActualFacts> facts, String targetCurrency, ZoneId queryZone) {
    if (facts == null || facts.isEmpty()) return List.of();

    String normalizedTarget = normalizeCode(targetCurrency);
    List<BudgetActualFacts> converted = new ArrayList<>(facts.size());
    for (BudgetActualFacts fact : facts) {
      if (fact == null
          || fact.eligibleAmount() == null
          || fact.eligibleAmount().signum() == 0
          || !StringUtils.hasText(fact.currency())
          || normalizeCode(fact.currency()).equals(normalizedTarget)) {
        converted.add(fact);
        continue;
      }

      if (fact.transactionTime() == null) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "跨币种预算 actual 缺少交易时间：" + fact.transactionId());
      }

      String sourceCurrency = normalizeCode(fact.currency());
      LocalDate rateDate = fact.transactionTime().atZone(queryZone).toLocalDate();
      ExchangeRateVo rate =
          exchangeRateService.effective(rateDate, sourceCurrency, normalizedTarget, null);

      BigDecimal convertedAmount =
          fact.eligibleAmount()
              .multiply(rate.exchangeRate())
              .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

      converted.add(
          new BudgetActualFacts(
              fact.transactionId(),
              fact.transactionType(),
              fact.originCode(),
              fact.originReferenceId(),
              fact.transactionTime(),
              convertedAmount,
              normalizedTarget,
              fact.targetFacts()));
    }
    return converted;
  }

  private void replaceChildren(String budgetId, List<BudgetSaveVo.Item> items) {
    List<BudgetItem> existing = budgetItemMapper.selectByBudgetId(budgetId);
    for (BudgetItem item : existing) {
      budgetTargetMapper.deleteByBudgetItemId(item.getBudgetItemId());
    }
    if (!existing.isEmpty()) {
      budgetItemMapper.deleteByBudgetId(budgetId);
    }

    int fallbackSort = 0;
    for (BudgetSaveVo.Item requestItem : items) {
      BudgetItem item = new BudgetItem();
      item.setBudgetItemId(UUID.randomUUID().toString());
      item.setBudgetId(budgetId);
      item.setBudgetAmount(requestItem.budgetAmount());
      item.setCurrency(normalizeCode(requestItem.currency()));
      item.setRolloverEnabled(Boolean.TRUE.equals(requestItem.rolloverEnabled()));
      item.setSortOrder(requestItem.sortOrder() == null ? fallbackSort : requestItem.sortOrder());
      budgetItemMapper.insert(item);

      for (BudgetSaveVo.Target requestTarget : safeTargets(requestItem.targets())) {
        BudgetTarget target = new BudgetTarget();
        target.setBudgetTargetId(UUID.randomUUID().toString());
        target.setBudgetItemId(item.getBudgetItemId());
        target.setTargetTypeCode(normalizeCode(requestTarget.targetTypeCode()));
        target.setTargetId(normalizeNullable(requestTarget.targetId()));
        target.setTargetValue(normalizeNullable(requestTarget.targetValue()));
        target.setIncludeDescendants(
            "CATEGORY".equals(target.getTargetTypeCode())
                && Boolean.TRUE.equals(requestTarget.includeDescendants()));
        budgetTargetMapper.insert(target);
      }
      fallbackSort += 10;
    }
  }

  private BudgetVo toVo(Budget budget) {
    List<BudgetItemVo> items =
        budgetItemMapper.selectByBudgetId(budget.getBudgetId()).stream()
            .map(
                item ->
                    new BudgetItemVo(
                        item.getBudgetItemId(),
                        item.getBudgetAmount(),
                        item.getCurrency(),
                        item.getRolloverEnabled(),
                        item.getSortOrder(),
                        budgetTargetMapper.selectByBudgetItemId(item.getBudgetItemId()).stream()
                            .map(this::toTargetVo)
                            .toList()))
            .toList();

    return new BudgetVo(
        budget.getBudgetId(),
        budget.getBudgetName(),
        budget.getPeriodTypeCode(),
        budget.getPeriodStart(),
        budget.getPeriodEnd(),
        budget.getBaseCurrency(),
        budget.getEnabled(),
        items);
  }

  private BudgetTargetVo toTargetVo(BudgetTarget target) {
    return new BudgetTargetVo(
        target.getBudgetTargetId(),
        target.getTargetTypeCode(),
        target.getTargetId(),
        target.getTargetValue(),
        target.getIncludeDescendants());
  }

  private Target toContractTarget(BudgetTarget target) {
    TargetType type;
    try {
      type = TargetType.valueOf(normalizeCode(target.getTargetTypeCode()));
    } catch (IllegalArgumentException error) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "预算目标类型无法识别：" + target.getTargetTypeCode());
    }
    return new Target(
        type,
        target.getTargetId(),
        target.getTargetValue(),
        Boolean.TRUE.equals(target.getIncludeDescendants()));
  }

  private void validateRequest(BudgetSaveVo request) {
    if (request == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算请求不能为空");
    }
    if (!StringUtils.hasText(request.budgetName())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算名称不能为空");
    }
    if (!PERIOD_TYPES.contains(normalizeCode(request.periodTypeCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算周期类型不正确");
    }
    if (request.periodStart() == null
        || request.periodEnd() == null
        || request.periodEnd().isBefore(request.periodStart())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算周期起止日期不正确");
    }
    if (!StringUtils.hasText(request.baseCurrency())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算基准币种不能为空");
    }
    if (request.items() == null || request.items().isEmpty()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算至少需要一个预算项");
    }

    int index = 0;
    for (BudgetSaveVo.Item item : request.items()) {
      validateItem(item, index++);
    }
    validateCategoryBudgetHierarchy(request.items());
  }

  private void validateCategoryBudgetHierarchy(List<BudgetSaveVo.Item> items) {
    Map<String, BudgetSaveVo.Item> categoryItems = new HashMap<>();
    Map<String, BudgetSaveVo.Target> categoryTargets = new HashMap<>();

    for (BudgetSaveVo.Item item : items) {
      BudgetSaveVo.Target target = pureCategoryTarget(item);
      if (target == null) continue;

      String categoryId = requireTargetId(target.targetId(), "分类");
      if (categoryItems.putIfAbsent(categoryId, item) != null) {
        throw new BusinessException(
            CommonErrorCode.DUPLICATE_DATA, "同一个分类不能配置多个独立分类预算项：" + categoryId);
      }
      categoryTargets.put(categoryId, target);
    }

    if (categoryItems.size() < 2) return;

    Map<String, BigDecimal> childTotals = new HashMap<>();
    for (Map.Entry<String, BudgetSaveVo.Item> entry : categoryItems.entrySet()) {
      String childCategoryId = entry.getKey();
      String parentCategoryId =
          findNearestConfiguredCategoryAncestor(childCategoryId, categoryItems.keySet());
      if (parentCategoryId == null) continue;

      BudgetSaveVo.Item child = entry.getValue();
      BudgetSaveVo.Item parent = categoryItems.get(parentCategoryId);
      if (!normalizeCode(child.currency()).equals(normalizeCode(parent.currency()))) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "子预算项币种必须与父预算项一致");
      }

      BudgetSaveVo.Target parentTarget = categoryTargets.get(parentCategoryId);
      if (parentTarget == null || !Boolean.TRUE.equals(parentTarget.includeDescendants())) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "存在子预算项时，父分类预算必须包含子分类");
      }

      childTotals.merge(parentCategoryId, child.budgetAmount(), BigDecimal::add);
    }

    for (Map.Entry<String, BigDecimal> entry : childTotals.entrySet()) {
      BudgetSaveVo.Item parent = categoryItems.get(entry.getKey());
      if (entry.getValue().compareTo(parent.budgetAmount()) > 0) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR,
            "子预算项合计不能超过父预算金额，父分类：" + entry.getKey());
      }
    }
  }

  private BudgetSaveVo.Target pureCategoryTarget(BudgetSaveVo.Item item) {
    // The first CATEGORY target is the hierarchy anchor. Additional CATEGORY targets
    // keep their original OR matching semantics and do not remove parent/child identity.
    return safeTargets(item.targets()).stream()
        .filter(target -> "CATEGORY".equals(normalizeCode(target.targetTypeCode())))
        .findFirst()
        .orElse(null);
  }

  private String findNearestConfiguredCategoryAncestor(
      String categoryId, Set<String> configuredCategoryIds) {
    Category category = categoryMapper.selectById(categoryId);
    Set<String> visited = new HashSet<>();
    visited.add(categoryId);

    String parentId = category == null ? null : category.getParentId();
    while (StringUtils.hasText(parentId) && visited.add(parentId)) {
      if (configuredCategoryIds.contains(parentId)) return parentId;
      Category parent = categoryMapper.selectById(parentId);
      if (parent == null) return null;
      parentId = parent.getParentId();
    }
    return null;
  }

  private void validateItem(BudgetSaveVo.Item item, int index) {
    if (item == null
        || item.budgetAmount() == null
        || item.budgetAmount().signum() < 0
        || !StringUtils.hasText(item.currency())) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "预算项金额和币种不正确，序号：" + index);
    }
    if (item.sortOrder() != null && item.sortOrder() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算项排序不能为负数");
    }

    Set<String> duplicateKeys = new HashSet<>();
    for (BudgetSaveVo.Target target : safeTargets(item.targets())) {
      String type = normalizeCode(target.targetTypeCode());
      TargetType targetType;
      try {
        targetType = TargetType.valueOf(type);
      } catch (Exception error) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的预算目标类型：" + type);
      }

      switch (targetType) {
        case CATEGORY -> {
          String targetId = requireTargetId(target.targetId(), "分类");
          requireReference(categoryMapper.selectById(targetId), "分类");
        }
        case ACCOUNT -> {
          String targetId = requireTargetId(target.targetId(), "账户");
          requireReference(accountMapper.selectById(targetId), "账户");
        }
        case PERSON -> {
          String targetId = requireTargetId(target.targetId(), "人员");
          requireReference(personMapper.selectById(targetId), "人员");
        }
        case MERCHANT -> {
          if (!StringUtils.hasText(target.targetValue())) {
            throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "商户预算目标值不能为空");
          }
        }
      }

      String key =
          type
              + "|"
              + normalizeNullable(target.targetId())
              + "|"
              + normalizeNullable(target.targetValue())
              + "|"
              + (targetType == TargetType.CATEGORY
                  && Boolean.TRUE.equals(target.includeDescendants()));
      if (!duplicateKeys.add(key)) {
        throw new BusinessException(CommonErrorCode.DUPLICATE_DATA, "预算项存在重复目标");
      }
    }
  }

  private String requireTargetId(String targetId, String label) {
    if (!StringUtils.hasText(targetId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, label + "预算目标ID不能为空");
    }
    return targetId.trim();
  }

  private void requireReference(Object entity, String label) {
    if (entity == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, label + "预算目标不存在");
    }
  }

  private List<BudgetSaveVo.Target> safeTargets(List<BudgetSaveVo.Target> targets) {
    return targets == null ? List.of() : targets;
  }

  private Budget requireBudget(String budgetId) {
    if (!StringUtils.hasText(budgetId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "预算ID不能为空");
    }
    Budget budget = budgetMapper.selectById(budgetId.trim());
    if (budget == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "预算不存在：" + budgetId);
    }
    return budget;
  }

  private void applyBudgetFields(Budget budget, BudgetSaveVo request) {
    budget.setBudgetName(request.budgetName().trim());
    budget.setPeriodTypeCode(normalizeCode(request.periodTypeCode()));
    budget.setPeriodStart(request.periodStart());
    budget.setPeriodEnd(request.periodEnd());
    budget.setBaseCurrency(normalizeCode(request.baseCurrency()));
    budget.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
  }

  private String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeNullable(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
