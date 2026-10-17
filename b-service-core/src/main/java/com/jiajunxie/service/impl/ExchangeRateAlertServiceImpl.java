package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.ExchangeRateAlertPairState;
import com.jiajunxie.entity.ExchangeRateAlertRule;
import com.jiajunxie.entity.ExchangeRateAlertSettings;
import com.jiajunxie.mapper.ExchangeRateAlertPairStateMapper;
import com.jiajunxie.mapper.ExchangeRateAlertRuleMapper;
import com.jiajunxie.mapper.ExchangeRateAlertSettingsMapper;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.FxPositionMapper;
import com.jiajunxie.service.BarkNotificationService;
import com.jiajunxie.service.DictionaryService;
import com.jiajunxie.service.ExchangeRateAlertService;
import com.jiajunxie.service.ExchangeRateService;
import com.jiajunxie.vo.BarkNotificationVo;
import com.jiajunxie.vo.DictionaryOptionVo;
import com.jiajunxie.vo.ExchangeRateAlertRuleVo;
import com.jiajunxie.vo.ExchangeRateAlertSettingsVo;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateAlertServiceImpl implements ExchangeRateAlertService {

  private static final int MIN_POLL_SECONDS = 10;
  private static final int MAX_POLL_SECONDS = 86400;
  private static final int MAX_COOLDOWN_MINUTES = 10080;

  private final ExchangeRateAlertSettingsMapper settingsMapper;
  private final ExchangeRateAlertRuleMapper ruleMapper;
  private final ExchangeRateAlertPairStateMapper pairStateMapper;
  private final FxPositionMapper fxPositionMapper;
  private final CurrencyInventoryBucketMapper currencyInventoryBucketMapper;
  private final CurrencyInventoryRebuildMapper currencyInventoryRebuildMapper;
  private final DictionaryService dictionaryService;
  private final ExchangeRateService exchangeRateService;
  private final BarkNotificationService barkNotificationService;
  private final AlertTemplateRenderer templateRenderer;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public ExchangeRateAlertSettingsVo getSettings() {
    return toSettingsVo(requireSettings());
  }

  @Override
  @Transactional
  public ExchangeRateAlertSettingsVo updateSettings(ExchangeRateAlertSettingsVo vo) {
    validateSettings(vo);
    ExchangeRateAlertSettings entity = requireSettings();

    entity.setMasterEnabled(Boolean.TRUE.equals(vo.masterEnabled()));
    entity.setPollingIntervalSeconds(vo.pollingIntervalSeconds());
    entity.setDefaultCooldownMinutes(vo.defaultCooldownMinutes());
    entity.setDefaultRateSourceCode(normalizeCode(vo.defaultRateSourceCode()));
    entity.setDefaultChannelCode(normalizeCode(vo.defaultChannelCode()));
    entity.setGlobalNotificationEnabled(Boolean.TRUE.equals(vo.globalNotificationEnabled()));
    entity.setGlobalNotificationTitle(trimToNull(vo.globalNotificationTitle()));
    entity.setGlobalNotificationBodyTemplate(trimToNull(vo.globalNotificationBodyTemplate()));
    entity.setGlobalNotificationUrl(trimToNull(vo.globalNotificationUrl()));
    entity.setBarkBaseUrl(vo.barkBaseUrl().trim());
    entity.setBarkSound(trimToNull(vo.barkSound()));
    entity.setBarkCall(vo.barkCall());
    entity.setBarkGroupName(trimToNull(vo.barkGroupName()));
    entity.setBarkLevel(trimToNull(vo.barkLevel()));
    entity.setBarkArchive(vo.barkArchive());
    entity.setBarkVolume(vo.barkVolume());
    entity.setUpdatedAt(Instant.now());
    settingsMapper.updateById(entity);

    eventPublisher.publishEvent(
        new ExchangeRateAlertSettingsChangedEvent(entity.getPollingIntervalSeconds()));
    return toSettingsVo(entity);
  }

  @Override
  public List<ExchangeRateAlertRuleVo> listRules(Boolean enabled) {
    return ruleMapper
        .selectList(
            Wrappers.<ExchangeRateAlertRule>lambdaQuery()
                .eq(enabled != null, ExchangeRateAlertRule::getEnabled, enabled)
                .orderByAsc(ExchangeRateAlertRule::getBaseCurrency)
                .orderByAsc(ExchangeRateAlertRule::getQuoteCurrency)
                .orderByAsc(ExchangeRateAlertRule::getThresholdRate)
                .orderByAsc(ExchangeRateAlertRule::getRuleName))
        .stream()
        .map(this::toRuleVo)
        .toList();
  }

  @Override
  @Transactional
  public ExchangeRateAlertRuleVo createRule(ExchangeRateAlertRuleVo vo) {
    ExchangeRateAlertSettings settings = requireSettings();
    validateRule(vo, settings);

    ExchangeRateAlertRule entity = new ExchangeRateAlertRule();
    entity.setAlertRuleId(UUID.randomUUID().toString());
    applyRule(entity, vo, true);
    Instant now = Instant.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    ruleMapper.insert(entity);
    return toRuleVo(entity);
  }

  @Override
  @Transactional
  public ExchangeRateAlertRuleVo updateRule(String alertRuleId, ExchangeRateAlertRuleVo vo) {
    ExchangeRateAlertRule entity = requireRule(alertRuleId);
    ExchangeRateAlertSettings settings = requireSettings();
    validateRule(vo, settings);
    applyRule(entity, vo, false);
    entity.setUpdatedAt(Instant.now());
    ruleMapper.updateById(entity);
    return toRuleVo(entity);
  }

  @Override
  @Transactional
  public int setRuleEnabled(String alertRuleId, boolean enabled) {
    ExchangeRateAlertRule entity = requireRule(alertRuleId);
    entity.setEnabled(enabled);
    if (!enabled) entity.setLastObservedRate(null);
    entity.setUpdatedAt(Instant.now());
    return ruleMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int deleteRule(String alertRuleId) {
    requireRule(alertRuleId);
    return ruleMapper.deleteById(alertRuleId);
  }

  /**
   * Evaluates alert rules once.
   *
   * <p>Rules are grouped by pair + effective source, so one quote is fetched only once per group.
   * A quote identical to the pair's previous quote suppresses the entire group before rule/global
   * notification selection. This intentionally prevents repeated weekend/closed-market pushes.
   *
   * <p>Winner order:
   * <ol>
   *   <li>Actual CROSS event winner, if any.
   *   <li>Nearest matching BELOW/ABOVE threshold winner.
   *   <li>Global notification policy when no special rule matches.
   * </ol>
   * Only one notification may be sent per pair/source group per polling round.
   */
  @Override
  @Transactional
  public void evaluateEnabledRules() {
    ExchangeRateAlertSettings settings = requireSettings();
    if (!Boolean.TRUE.equals(settings.getMasterEnabled())) return;

    List<ExchangeRateAlertRule> rules =
        ruleMapper.selectList(
            Wrappers.<ExchangeRateAlertRule>lambdaQuery()
                .eq(ExchangeRateAlertRule::getEnabled, true)
                .orderByAsc(ExchangeRateAlertRule::getBaseCurrency)
                .orderByAsc(ExchangeRateAlertRule::getQuoteCurrency)
                .orderByAsc(ExchangeRateAlertRule::getRuleName));

    if (rules.isEmpty()) return;

    Map<String, RuleGroup> groups = buildGroups(rules, settings);
    Instant now = Instant.now();

    for (RuleGroup group : groups.values()) {
      QuoteValue quote;
      try {
        quote = loadQuote(group.baseCurrency(), group.quoteCurrency(), group.sourceCode());
      } catch (RuntimeException ex) {
        log.warn(
            "汇率提醒获取行情失败 pair={}/{} source={} error={}",
            group.baseCurrency(),
            group.quoteCurrency(),
            group.sourceCode(),
            ex.getMessage());
        continue;
      }

      ExchangeRateAlertPairState pairState =
          requirePairState(group.baseCurrency(), group.quoteCurrency(), group.sourceCode());
      BigDecimal previousRate = pairState.getLastObservedRate();
      BigDecimal currentRate = quote.rate();

      // Closed-market/stale-quote suppression. Scale differences do not count as movement.
      if (previousRate != null && currentRate.compareTo(previousRate) == 0) {
        updateRuleObservations(group.rules(), currentRate, now);
        pairState.setUpdatedAt(now);
        pairStateMapper.updateById(pairState);
        log.debug(
            "汇率未变化，跳过通知 pair={}/{} rate={}",
            group.baseCurrency(),
            group.quoteCurrency(),
            currentRate);
        continue;
      }

      BigDecimal averageCostRate =
          currencyInventoryRebuildMapper.selectInventoryCutoverAppliedAt() == null
              ? fxPositionMapper.selectSystemWeightedAverageRate(
                  group.baseCurrency(), group.quoteCurrency())
              : currencyInventoryBucketMapper.selectSystemWeightedAverageRate(
                  group.baseCurrency(), group.quoteCurrency());

      RuleMatch winner =
          selectCrossWinner(group.rules(), previousRate, currentRate, averageCostRate);
      if (winner == null) {
        winner = selectStateWinner(group.rules(), currentRate, averageCostRate);
      }

      if (winner != null) {
        ExchangeRateAlertRule winnerRule = winner.rule();
        if (cooldownPassed(
            winnerRule.getLastTriggeredAt(), effectiveCooldown(winnerRule, settings), now)) {
          boolean sent =
              sendRuleNotification(
                  winnerRule,
                  winner.thresholdRate(),
                  settings,
                  previousRate,
                  currentRate,
                  averageCostRate,
                  group.sourceCode());
          if (sent) winnerRule.setLastTriggeredAt(now);
        }
      } else if (Boolean.TRUE.equals(settings.getGlobalNotificationEnabled())) {
        if (cooldownPassed(
            pairState.getLastGlobalTriggeredAt(), settings.getDefaultCooldownMinutes(), now)) {
          boolean sent =
              sendGlobalNotification(
                  group.baseCurrency(),
                  group.quoteCurrency(),
                  settings,
                  previousRate,
                  currentRate,
                  averageCostRate,
                  group.sourceCode());
          if (sent) pairState.setLastGlobalTriggeredAt(now);
        }
      }

      updateRuleObservations(group.rules(), currentRate, now);
      pairState.setLastObservedRate(currentRate);
      pairState.setUpdatedAt(now);
      pairStateMapper.updateById(pairState);
    }
  }

  private Map<String, RuleGroup> buildGroups(
      List<ExchangeRateAlertRule> rules, ExchangeRateAlertSettings settings) {
    Map<String, RuleGroup> groups = new LinkedHashMap<>();
    for (ExchangeRateAlertRule rule : rules) {
      String base = normalizeCurrency(rule.getBaseCurrency());
      String quote = normalizeCurrency(rule.getQuoteCurrency());
      String source =
          StringUtils.hasText(rule.getRateSourceCode())
              ? normalizeCode(rule.getRateSourceCode())
              : normalizeCode(settings.getDefaultRateSourceCode());
      String key = base + "/" + quote + "/" + source;
      RuleGroup group =
          groups.computeIfAbsent(
              key, ignored -> new RuleGroup(base, quote, source, new ArrayList<>()));
      group.rules().add(rule);
    }
    return groups;
  }

  private RuleMatch selectCrossWinner(
      List<ExchangeRateAlertRule> rules,
      BigDecimal previousRate,
      BigDecimal currentRate,
      BigDecimal averageCostRate) {
    if (previousRate == null) return null;

    List<RuleMatch> crossed =
        rules.stream()
            .filter(rule -> isCrossRule(rule.getOperatorCode()))
            // CROSS keeps per-rule observation semantics. A newly created, edited, or
            // re-enabled rule must observe once before it is allowed to report a crossing,
            // even when the pair itself already has historical observations.
            .filter(rule -> rule.getLastObservedRate() != null)
            .map(rule -> resolveRuleMatch(rule, averageCostRate))
            .filter(java.util.Objects::nonNull)
            .filter(
                match ->
                    matchesCross(
                        match.rule().getOperatorCode(),
                        match.rule().getLastObservedRate(),
                        currentRate,
                        match.thresholdRate()))
            .toList();

    if (crossed.isEmpty()) return null;

    boolean movedDown = currentRate.compareTo(previousRate) < 0;
    Comparator<RuleMatch> comparator =
        movedDown
            ? Comparator.comparing(RuleMatch::thresholdRate)
            : Comparator.comparing(RuleMatch::thresholdRate).reversed();

    return crossed.stream()
        .sorted(comparator.thenComparing(match -> match.rule().getAlertRuleId()))
        .findFirst()
        .orElse(null);
  }

  private RuleMatch selectStateWinner(
      List<ExchangeRateAlertRule> rules,
      BigDecimal currentRate,
      BigDecimal averageCostRate) {
    return rules.stream()
        .filter(rule -> isStateRule(rule.getOperatorCode()))
        .map(rule -> resolveRuleMatch(rule, averageCostRate))
        .filter(java.util.Objects::nonNull)
        .filter(
            match ->
                matchesState(
                    match.rule().getOperatorCode(), currentRate, match.thresholdRate()))
        .min(
            Comparator.comparing(
                    (RuleMatch match) ->
                        currentRate.subtract(match.thresholdRate()).abs())
                .thenComparing(RuleMatch::thresholdRate)
                .thenComparing(match -> match.rule().getAlertRuleId()))
        .orElse(null);
  }

  private RuleMatch resolveRuleMatch(
      ExchangeRateAlertRule rule, BigDecimal averageCostRate) {
    String mode =
        StringUtils.hasText(rule.getThresholdModeCode())
            ? normalizeCode(rule.getThresholdModeCode())
            : "FIXED";

    BigDecimal threshold =
        switch (mode) {
          case "FIXED" -> rule.getThresholdRate();
          case "AVERAGE_COST" -> averageCostRate;
          default -> null;
        };

    if (threshold == null || threshold.signum() <= 0) {
      log.debug(
          "提醒规则阈值不可用，跳过特殊 Rule rule={} mode={}",
          rule.getRuleName(),
          mode);
      return null;
    }
    return new RuleMatch(rule, threshold);
  }

  private boolean matchesState(
      String operatorCode, BigDecimal currentRate, BigDecimal threshold) {
    int cmp = currentRate.compareTo(threshold);
    return switch (normalizeCode(operatorCode)) {
      case "ABOVE" -> cmp >= 0;
      case "BELOW" -> cmp <= 0;
      default -> false;
    };
  }

  private boolean matchesCross(
      String operatorCode,
      BigDecimal previousRate,
      BigDecimal currentRate,
      BigDecimal threshold) {
    return switch (normalizeCode(operatorCode)) {
      case "CROSS_ABOVE" ->
          previousRate.compareTo(threshold) < 0 && currentRate.compareTo(threshold) >= 0;
      case "CROSS_BELOW" ->
          previousRate.compareTo(threshold) > 0 && currentRate.compareTo(threshold) <= 0;
      default -> false;
    };
  }

  private boolean isStateRule(String operatorCode) {
    String value = normalizeCode(operatorCode);
    return "ABOVE".equals(value) || "BELOW".equals(value);
  }

  private boolean isCrossRule(String operatorCode) {
    String value = normalizeCode(operatorCode);
    return "CROSS_ABOVE".equals(value) || "CROSS_BELOW".equals(value);
  }

  private int effectiveCooldown(
      ExchangeRateAlertRule rule, ExchangeRateAlertSettings settings) {
    return rule.getCooldownMinutes() == null
        ? settings.getDefaultCooldownMinutes()
        : rule.getCooldownMinutes();
  }

  private boolean cooldownPassed(Instant lastTriggeredAt, int cooldownMinutes, Instant now) {
    if (lastTriggeredAt == null) return true;
    return !now.isBefore(lastTriggeredAt.plus(cooldownMinutes, ChronoUnit.MINUTES));
  }

  private boolean sendRuleNotification(
      ExchangeRateAlertRule rule,
      BigDecimal effectiveThresholdRate,
      ExchangeRateAlertSettings settings,
      BigDecimal previousRate,
      BigDecimal currentRate,
      BigDecimal averageCostRate,
      String source) {
    String channel =
        StringUtils.hasText(rule.getChannelCode())
            ? normalizeCode(rule.getChannelCode())
            : normalizeCode(settings.getDefaultChannelCode());
    if (!"BARK".equals(channel)) return false;

    String title =
        StringUtils.hasText(rule.getNotificationTitle())
            ? rule.getNotificationTitle().trim()
            : rule.getRuleName();

    String defaultBody =
        "{baseCurrency}/{quoteCurrency} 当前汇率：{currentRate}\n条件：{operator} {thresholdRate}\n来源：{rateSource}";
    String body =
        renderTemplate(
            StringUtils.hasText(rule.getNotificationBodyTemplate())
                ? rule.getNotificationBodyTemplate()
                : defaultBody,
            rule,
            effectiveThresholdRate,
            previousRate,
            currentRate,
            averageCostRate,
            source);

    return barkNotificationService.send(
        settings.getBarkBaseUrl(),
        BarkNotificationVo.builder()
            .title(
                renderTemplate(
                    title,
                    rule,
                    effectiveThresholdRate,
                    previousRate,
                    currentRate,
                    averageCostRate,
                    source))
            .body(body)
            .url(
                effectiveNotificationUrl(
                    rule.getNotificationUrl(),
                    settings.getGlobalNotificationUrl(),
                    rule.getBaseCurrency(),
                    rule.getQuoteCurrency(),
                    source))
            .sound(
                StringUtils.hasText(rule.getBarkSound())
                    ? rule.getBarkSound().trim()
                    : settings.getBarkSound())
            .call(rule.getBarkCall() != null ? rule.getBarkCall() : settings.getBarkCall())
            .group(settings.getBarkGroupName())
            .level(
                StringUtils.hasText(rule.getBarkLevel())
                    ? rule.getBarkLevel().trim()
                    : settings.getBarkLevel())
            .isArchive(
                rule.getBarkArchive() != null ? rule.getBarkArchive() : settings.getBarkArchive())
            .volume(rule.getBarkVolume() != null ? rule.getBarkVolume() : settings.getBarkVolume())
            .build());
  }

  private boolean sendGlobalNotification(
      String baseCurrency,
      String quoteCurrency,
      ExchangeRateAlertSettings settings,
      BigDecimal previousRate,
      BigDecimal currentRate,
      BigDecimal averageCostRate,
      String source) {
    if (!"BARK".equals(normalizeCode(settings.getDefaultChannelCode()))) return false;

    String titleTemplate =
        StringUtils.hasText(settings.getGlobalNotificationTitle())
            ? settings.getGlobalNotificationTitle()
            : "{baseCurrency}/{quoteCurrency} 汇率通知";
    String bodyTemplate =
        StringUtils.hasText(settings.getGlobalNotificationBodyTemplate())
            ? settings.getGlobalNotificationBodyTemplate()
            : "{baseCurrency}/{quoteCurrency} 当前汇率：{currentRate}\n来源：{rateSource}";

    ExchangeRateAlertRule synthetic = new ExchangeRateAlertRule();
    synthetic.setRuleName("GLOBAL");
    synthetic.setBaseCurrency(baseCurrency);
    synthetic.setQuoteCurrency(quoteCurrency);
    synthetic.setOperatorCode("GLOBAL");
    synthetic.setThresholdRate(BigDecimal.ZERO);

    return barkNotificationService.send(
        settings.getBarkBaseUrl(),
        BarkNotificationVo.builder()
            .title(
                renderTemplate(
                    titleTemplate,
                    synthetic,
                    BigDecimal.ZERO,
                    previousRate,
                    currentRate,
                    averageCostRate,
                    source))
            .body(
                renderTemplate(
                    bodyTemplate,
                    synthetic,
                    BigDecimal.ZERO,
                    previousRate,
                    currentRate,
                    averageCostRate,
                    source))
            .url(
                effectiveNotificationUrl(
                    null,
                    settings.getGlobalNotificationUrl(),
                    baseCurrency,
                    quoteCurrency,
                    source))
            .sound(settings.getBarkSound())
            .call(settings.getBarkCall())
            .group(settings.getBarkGroupName())
            .level(settings.getBarkLevel())
            .isArchive(settings.getBarkArchive())
            .volume(settings.getBarkVolume())
            .build());
  }

  private String renderTemplate(
      String template,
      ExchangeRateAlertRule rule,
      BigDecimal effectiveThresholdRate,
      BigDecimal previousRate,
      BigDecimal currentRate,
      BigDecimal averageCostRate,
      String source) {
    Map<String, String> textVariables =
        Map.of(
            "ruleName", safe(rule.getRuleName()),
            "baseCurrency", safe(rule.getBaseCurrency()),
            "quoteCurrency", safe(rule.getQuoteCurrency()),
            "operator", safe(rule.getOperatorCode()),
            "rateSource", safe(source));

    Map<String, BigDecimal> numericVariables = new java.util.HashMap<>();
    numericVariables.put("currentRate", currentRate);
    numericVariables.put("previousRate", previousRate);
    numericVariables.put("thresholdRate", effectiveThresholdRate);
    numericVariables.put("averageCostRate", averageCostRate);

    return templateRenderer.render(template, textVariables, numericVariables);
  }

  private String effectiveNotificationUrl(
      String ruleUrl,
      String globalUrl,
      String base,
      String quote,
      String source) {
    if (StringUtils.hasText(ruleUrl)) return ruleUrl.trim();
    if (StringUtils.hasText(globalUrl)) return globalUrl.trim();
    return tradingViewUrl(base, quote, source);
  }

  private String tradingViewUrl(String base, String quote, String source) {
    return "TRADINGVIEW".equals(source)
        ? "https://www.tradingview.com/symbols/" + base + quote + "/"
        : null;
  }

  private void updateRuleObservations(
      List<ExchangeRateAlertRule> rules, BigDecimal currentRate, Instant now) {
    for (ExchangeRateAlertRule rule : rules) {
      rule.setLastObservedRate(currentRate);
      rule.setUpdatedAt(now);
      ruleMapper.updateById(rule);
    }
  }

  private QuoteValue loadQuote(String base, String quote, String source) {
    if ("TRADINGVIEW".equals(source)) {
      RealtimeExchangeRateVo realtime = exchangeRateService.realtime(base, quote);
      return new QuoteValue(
          realtime.exchangeRate(),
          realtime.rateSourceCode(),
          null,
          realtime.observedAt(),
          realtime.derived());
    }

    ExchangeRateVo stored = exchangeRateService.effective(LocalDate.now(), base, quote, source);
    return new QuoteValue(
        stored.exchangeRate(),
        stored.rateSourceCode(),
        stored.rateDate(),
        null,
        stored.derived());
  }

  private ExchangeRateAlertPairState requirePairState(
      String baseCurrency, String quoteCurrency, String sourceCode) {
    ExchangeRateAlertPairState existing =
        pairStateMapper.selectOne(
            Wrappers.<ExchangeRateAlertPairState>lambdaQuery()
                .eq(ExchangeRateAlertPairState::getBaseCurrency, baseCurrency)
                .eq(ExchangeRateAlertPairState::getQuoteCurrency, quoteCurrency)
                .eq(ExchangeRateAlertPairState::getRateSourceCode, sourceCode));
    if (existing != null) return existing;

    ExchangeRateAlertPairState created = new ExchangeRateAlertPairState();
    created.setPairStateId(UUID.randomUUID().toString());
    created.setBaseCurrency(baseCurrency);
    created.setQuoteCurrency(quoteCurrency);
    created.setRateSourceCode(sourceCode);
    Instant now = Instant.now();
    created.setCreatedAt(now);
    created.setUpdatedAt(now);
    pairStateMapper.insert(created);
    return created;
  }

  private ExchangeRateAlertSettings requireSettings() {
    ExchangeRateAlertSettings settings =
        settingsMapper.selectById(ExchangeRateAlertSettings.DEFAULT_SETTINGS_ID);
    if (settings != null) return settings;

    ExchangeRateAlertSettings created = new ExchangeRateAlertSettings();
    created.setSettingsId(ExchangeRateAlertSettings.DEFAULT_SETTINGS_ID);
    Instant now = Instant.now();
    created.setCreatedAt(now);
    created.setUpdatedAt(now);
    settingsMapper.insert(created);
    return created;
  }

  private ExchangeRateAlertRule requireRule(String alertRuleId) {
    if (!StringUtils.hasText(alertRuleId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "提醒规则 ID 不能为空");
    }
    ExchangeRateAlertRule rule = ruleMapper.selectById(alertRuleId);
    if (rule == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "汇率提醒规则不存在");
    }
    return rule;
  }

  private void validateSettings(ExchangeRateAlertSettingsVo vo) {
    if (vo == null
        || vo.masterEnabled() == null
        || vo.globalNotificationEnabled() == null
        || vo.pollingIntervalSeconds() == null
        || vo.defaultCooldownMinutes() == null
        || !StringUtils.hasText(vo.defaultRateSourceCode())
        || !StringUtils.hasText(vo.defaultChannelCode())
        || !StringUtils.hasText(vo.barkBaseUrl())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "汇率提醒设置不完整");
    }
    if (vo.pollingIntervalSeconds() < MIN_POLL_SECONDS
        || vo.pollingIntervalSeconds() > MAX_POLL_SECONDS) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR,
          "行情检查周期必须在 " + MIN_POLL_SECONDS + "～" + MAX_POLL_SECONDS + " 秒之间");
    }
    if (vo.defaultCooldownMinutes() < 0
        || vo.defaultCooldownMinutes() > MAX_COOLDOWN_MINUTES) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "默认冷却时间超出允许范围");
    }
    requireDictionaryOption("RATE_SOURCE", normalizeCode(vo.defaultRateSourceCode()));
    requireDictionaryOption("FX_ALERT_CHANNEL", normalizeCode(vo.defaultChannelCode()));
    validateBarkStyle(vo.barkCall(), vo.barkArchive(), vo.barkVolume());
    validateOptionalUrl(vo.globalNotificationUrl(), "全局通知 URL");
  }

  private void validateRule(
      ExchangeRateAlertRuleVo vo, ExchangeRateAlertSettings settings) {
    if (vo == null
        || !StringUtils.hasText(vo.ruleName())
        || !StringUtils.hasText(vo.baseCurrency())
        || !StringUtils.hasText(vo.quoteCurrency())
        || !StringUtils.hasText(vo.operatorCode())
        || !StringUtils.hasText(vo.thresholdModeCode())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "汇率提醒规则不完整");
    }

    String thresholdMode = normalizeCode(vo.thresholdModeCode());
    if (!"FIXED".equals(thresholdMode) && !"AVERAGE_COST".equals(thresholdMode)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "不支持的提醒阈值来源：" + thresholdMode);
    }
    requireDictionaryOption("FX_ALERT_THRESHOLD_MODE", thresholdMode);
    if ("FIXED".equals(thresholdMode)
        && (vo.thresholdRate() == null || vo.thresholdRate().signum() <= 0)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "固定汇率阈值必须大于 0");
    }

    String base = normalizeCurrency(vo.baseCurrency());
    String quote = normalizeCurrency(vo.quoteCurrency());
    if (base.equals(quote)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "提醒规则币种不能相同");
    }

    requireDictionaryOption("FX_ALERT_OPERATOR", normalizeCode(vo.operatorCode()));

    String source =
        StringUtils.hasText(vo.rateSourceCode())
            ? normalizeCode(vo.rateSourceCode())
            : normalizeCode(settings.getDefaultRateSourceCode());
    requireDictionaryOption("RATE_SOURCE", source);

    String channel =
        StringUtils.hasText(vo.channelCode())
            ? normalizeCode(vo.channelCode())
            : normalizeCode(settings.getDefaultChannelCode());
    requireDictionaryOption("FX_ALERT_CHANNEL", channel);

    if (vo.cooldownMinutes() != null
        && (vo.cooldownMinutes() < 0 || vo.cooldownMinutes() > MAX_COOLDOWN_MINUTES)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "规则冷却时间超出允许范围");
    }
    validateBarkStyle(vo.barkCall(), vo.barkArchive(), vo.barkVolume());
    validateOptionalUrl(vo.notificationUrl(), "规则通知 URL");
  }

  private void applyRule(
      ExchangeRateAlertRule entity, ExchangeRateAlertRuleVo vo, boolean creating) {
    entity.setRuleName(vo.ruleName().trim());
    entity.setBaseCurrency(normalizeCurrency(vo.baseCurrency()));
    entity.setQuoteCurrency(normalizeCurrency(vo.quoteCurrency()));
    entity.setRateSourceCode(
        StringUtils.hasText(vo.rateSourceCode()) ? normalizeCode(vo.rateSourceCode()) : null);
    entity.setOperatorCode(normalizeCode(vo.operatorCode()));
    entity.setThresholdModeCode(normalizeCode(vo.thresholdModeCode()));
    entity.setThresholdRate(
        "FIXED".equals(normalizeCode(vo.thresholdModeCode())) ? vo.thresholdRate() : null);
    entity.setChannelCode(
        StringUtils.hasText(vo.channelCode()) ? normalizeCode(vo.channelCode()) : null);
    entity.setCooldownMinutes(vo.cooldownMinutes());
    entity.setNotificationTitle(trimToNull(vo.notificationTitle()));
    entity.setNotificationBodyTemplate(trimToNull(vo.notificationBodyTemplate()));
    entity.setNotificationUrl(trimToNull(vo.notificationUrl()));
    entity.setBarkLevel(trimToNull(vo.barkLevel()));
    entity.setBarkSound(trimToNull(vo.barkSound()));
    entity.setBarkCall(vo.barkCall());
    entity.setBarkArchive(vo.barkArchive());
    entity.setBarkVolume(vo.barkVolume());
    entity.setEnabled(vo.enabled() == null ? Boolean.TRUE : vo.enabled());
    if (!creating) entity.setLastObservedRate(null);
  }

  private void requireDictionaryOption(String typeCode, String value) {
    List<DictionaryOptionVo> options = dictionaryService.listOptions(typeCode, value);
    boolean exists =
        options.stream().anyMatch(option -> value.equalsIgnoreCase(option.getValue()));
    if (!exists) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, typeCode + " 字典项未启用或不存在：" + value);
    }
  }

  private void validateOptionalUrl(String value, String fieldName) {
    if (!StringUtils.hasText(value)) return;
    String url = value.trim();
    if (!(url.startsWith("http://")
        || url.startsWith("https://")
        || url.matches("^[A-Za-z][A-Za-z0-9+.-]*://.+$"))) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, fieldName + " 必须是有效 URL 或 App Scheme");
    }
  }

  private void validateBarkStyle(Integer call, Integer archive, Double volume) {
    if (call != null && call != 0 && call != 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "Bark call 只能是 0 或 1");
    }
    if (archive != null && archive != 0 && archive != 1) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "Bark archive 只能是 0 或 1");
    }
    if (volume != null && (volume < 0 || volume > 10)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "Bark volume 必须在 0～10 之间");
    }
  }

  private ExchangeRateAlertSettingsVo toSettingsVo(ExchangeRateAlertSettings entity) {
    return new ExchangeRateAlertSettingsVo(
        entity.getMasterEnabled(),
        entity.getPollingIntervalSeconds(),
        entity.getDefaultCooldownMinutes(),
        entity.getDefaultRateSourceCode(),
        entity.getDefaultChannelCode(),
        entity.getGlobalNotificationEnabled(),
        entity.getGlobalNotificationTitle(),
        entity.getGlobalNotificationBodyTemplate(),
        entity.getGlobalNotificationUrl(),
        entity.getBarkBaseUrl(),
        entity.getBarkSound(),
        entity.getBarkCall(),
        entity.getBarkGroupName(),
        entity.getBarkLevel(),
        entity.getBarkArchive(),
        entity.getBarkVolume(),
        barkNotificationService.isConfigured());
  }

  private ExchangeRateAlertRuleVo toRuleVo(ExchangeRateAlertRule entity) {
    return new ExchangeRateAlertRuleVo(
        entity.getAlertRuleId(),
        entity.getRuleName(),
        entity.getBaseCurrency(),
        entity.getQuoteCurrency(),
        entity.getRateSourceCode(),
        entity.getOperatorCode(),
        entity.getThresholdModeCode(),
        entity.getThresholdRate(),
        entity.getChannelCode(),
        entity.getCooldownMinutes(),
        entity.getNotificationTitle(),
        entity.getNotificationBodyTemplate(),
        entity.getNotificationUrl(),
        entity.getBarkLevel(),
        entity.getBarkSound(),
        entity.getBarkCall(),
        entity.getBarkArchive(),
        entity.getBarkVolume(),
        entity.getEnabled(),
        entity.getLastObservedRate(),
        entity.getLastTriggeredAt());
  }

  private String normalizeCurrency(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private record RuleMatch(
      ExchangeRateAlertRule rule,
      BigDecimal thresholdRate) {}

  private record RuleGroup(
      String baseCurrency,
      String quoteCurrency,
      String sourceCode,
      List<ExchangeRateAlertRule> rules) {}

  private record QuoteValue(
      BigDecimal rate,
      String sourceCode,
      LocalDate rateDate,
      Instant observedAt,
      boolean derived) {}
}
