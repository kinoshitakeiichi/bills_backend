package com.jiajunxie.service.impl;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.inventory.CurrencyInventoryAccountSnapshot;
import com.jiajunxie.domain.inventory.CurrencyInventoryReplayRow;
import com.jiajunxie.entity.CurrencyInventoryBucket;
import com.jiajunxie.entity.FxDetail;
import com.jiajunxie.mapper.CurrencyInventoryBucketMapper;
import com.jiajunxie.mapper.CurrencyInventoryRebuildMapper;
import com.jiajunxie.mapper.FxDetailMapper;
import com.jiajunxie.service.CurrencyInventoryRebuildService;
import com.jiajunxie.vo.CurrencyInventoryAccountPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildApplyRequestVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildApplyResultVo;
import com.jiajunxie.vo.CurrencyInventoryBucketPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildIssueVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildRequestVo;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FX Domain V3 current FX-cost reconstruction preview.
 *
 * <p>This service intentionally does <strong>not</strong> rebuild the full provenance of every
 * currency unit in every ASSET account. TransactionEntry remains the balance source of truth. The
 * preview only reconstructs the part of each current account balance that still carries a direct
 * FX acquisition cost, grouped by source currency.
 *
 * <p>Ordinary income/opening/interest/refund money is represented implicitly as the non-FX residual
 * ({@code account balance - FX bucket quantity}). Ordinary disposals and same-currency transfers
 * only matter when an account currently contains FX-cost quantity; in that case the FX buckets are
 * depleted pro-rata against the full pre-transaction account balance.
 *
 * <p>Historical cutover is deliberately conservative: only accounts with a positive legacy V2
 * {@code b_fx_positions} row are eligible to receive reconstructed pre-cutover FX buckets. The
 * legacy position values themselves are never copied; real FxDetail/Entry history derives V3 cost.
 * This prevents V3 migration from inventing current reverse-cost buckets on ordinary JPY (or other)
 * accounts that V2 never maintained and whose old intra/cross-day ordering is not trustworthy.
 *
 * <p>Preview is read-only. V3-3A also exposes a separately guarded one-time Apply that persists
 * only the reviewed current FX-cost snapshot. V3-3B write logic activates automatically only after
 * this cutover marker exists; production Apply remains fail-closed until the V3 read path is ready.
 */
@Service
public class CurrencyInventoryRebuildServiceImpl implements CurrencyInventoryRebuildService {
  private static final int MONEY_SCALE = 4;
  private static final int RATE_SCALE = 10;
  private static final String ORIGIN_FX = "FX_EXCHANGE";
  private static final String APPLY_CONFIRMATION = "APPLY_FX_COST_INVENTORY_V3";

  private final CurrencyInventoryRebuildMapper rebuildMapper;
  private final FxDetailMapper fxDetailMapper;
  private final CurrencyInventoryBucketMapper bucketMapper;
  private final boolean applyEnabled;

  public CurrencyInventoryRebuildServiceImpl(
      CurrencyInventoryRebuildMapper rebuildMapper,
      FxDetailMapper fxDetailMapper,
      CurrencyInventoryBucketMapper bucketMapper,
      @Value("${fx.inventory.apply-enabled:false}") boolean applyEnabled) {
    this.rebuildMapper = rebuildMapper;
    this.fxDetailMapper = fxDetailMapper;
    this.bucketMapper = bucketMapper;
    this.applyEnabled = applyEnabled;
  }

  @Override
  @Transactional(readOnly = true)
  public CurrencyInventoryRebuildPreviewVo preview(CurrencyInventoryRebuildRequestVo request) {
    return buildPreview(request);
  }

  @Override
  @Transactional
  public CurrencyInventoryRebuildApplyResultVo apply(CurrencyInventoryRebuildApplyRequestVo request) {
    if (!applyEnabled) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "FX Cost Inventory Apply 当前被配置禁用；V3 read-path 切换完成前不得执行生产 cutover");
    }
    if (request == null
        || request.expectedFingerprint() == null
        || request.expectedFingerprint().isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "expectedFingerprint 不能为空");
    }
    if (!APPLY_CONFIRMATION.equals(request.confirmation())) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR,
          "confirmation 必须精确等于 " + APPLY_CONFIRMATION);
    }

    // Serialize the one-time cutover against normal ASSET account writes, then re-read every fact
    // inside the same transaction. Apply never trusts the earlier HTTP Preview payload.
    rebuildMapper.lockAssetAccountsForUpdate();

    long existingBuckets = rebuildMapper.countInventoryBuckets();
    long existingMovements = rebuildMapper.countInventoryMovements();
    long existingCutovers = rebuildMapper.countInventoryCutoverRuns();
    if (existingBuckets != 0 || existingMovements != 0 || existingCutovers != 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "FX Cost Inventory cutover 仅允许从空 V3 状态执行一次：buckets="
              + existingBuckets
              + ", movements="
              + existingMovements
              + ", cutovers="
              + existingCutovers);
    }

    CurrencyInventoryRebuildPreviewVo preview =
        buildPreview(new CurrencyInventoryRebuildRequestVo(List.of()));
    if (!preview.applyEligible()) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "当前 FX Cost Preview 存在 "
              + preview.globalIssueCount()
              + " 个 blocker，拒绝持久化 cutover");
    }
    if (!preview.fingerprint().equals(request.expectedFingerprint().trim())) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "Preview fingerprint 已变化；请重新执行完整 Preview 并重新审计后再 Apply");
    }

    Instant appliedAt = Instant.now();
    int bucketCount = persistCutoverBuckets(preview, appliedAt);
    String cutoverId = UUID.randomUUID().toString();
    int cutoverInserted =
        rebuildMapper.insertInventoryCutoverRun(
            cutoverId,
            preview.fingerprint(),
            appliedAt,
            preview.replayTransactionCount(),
            preview.replayAccountCount(),
            bucketCount,
            0);
    if (cutoverInserted != 1) {
      throw new IllegalStateException("FX Cost Inventory cutover marker insert failed");
    }

    return new CurrencyInventoryRebuildApplyResultVo(
        cutoverId, appliedAt, preview.fingerprint(), bucketCount, 0);
  }

  private CurrencyInventoryRebuildPreviewVo buildPreview(CurrencyInventoryRebuildRequestVo request) {
    List<CurrencyInventoryAccountSnapshot> snapshots = rebuildMapper.selectAssetAccounts();
    Map<String, AccountState> states = new LinkedHashMap<>();
    for (CurrencyInventoryAccountSnapshot snapshot : snapshots) {
      states.put(snapshot.getAccountId(), new AccountState(snapshot));
    }

    Set<String> requested = normalizeRequestedIds(request);
    if (!requested.isEmpty()) {
      List<String> missing = requested.stream().filter(id -> !states.containsKey(id)).sorted().toList();
      if (!missing.isEmpty()) {
        throw new BusinessException(
            CommonErrorCode.DATA_NOT_FOUND,
            "FX Cost Inventory Preview 仅支持现有 ASSET 账户，未找到：" + missing);
      }
    }

    Set<String> legacyFxAccounts = legacyFxAccountIds(states);
    for (String accountId : legacyFxAccounts) {
      AccountState state = states.get(accountId);
      if (state != null) state.legacyFxEligible = true;
    }

    List<CurrencyInventoryRebuildIssueVo> allIssues = new ArrayList<>();
    List<CurrencyInventoryReplayRow> rows = rebuildMapper.selectAssetReplayRows();
    List<TransactionReplay> transactions = groupTransactions(rows);
    replayTransactions(transactions, states, allIssues);

    // Historical cutover only migrates accounts that legacy V2 was actually tracking as a current
    // FX position. Ordinary ASSET accounts remain authoritative via Account/TransactionEntry and
    // must not block FX migration merely because their old time ordering cannot be reconstructed.
    addFinalInvariantIssues(states, allIssues);

    Set<String> issueScope =
        requested.isEmpty()
            ? new LinkedHashSet<>(states.keySet())
            : expandFxDependencyScope(requested, transactions);
    List<CurrencyInventoryRebuildIssueVo> scopedIssues =
        requested.isEmpty()
            ? List.copyOf(allIssues)
            : scopeIssues(allIssues, issueScope, transactions);

    List<CurrencyInventoryAccountPreviewVo> accountReports = new ArrayList<>();
    for (AccountState state : states.values()) {
      if (!requested.isEmpty() && !requested.contains(state.accountId)) continue;
      accountReports.add(toAccountPreview(state, scopedIssues));
    }

    int replayTransactionCount =
        requested.isEmpty()
            ? transactions.size()
            : countTransactionsTouchingScope(transactions, issueScope);
    int replayAccountCount = requested.isEmpty() ? states.size() : issueScope.size();

    String fingerprint =
        fingerprint(replayTransactionCount, replayAccountCount, accountReports, scopedIssues);
    return new CurrencyInventoryRebuildPreviewVo(
        Instant.now(),
        fingerprint,
        replayTransactionCount,
        replayAccountCount,
        scopedIssues.size(),
        scopedIssues.isEmpty(),
        accountReports,
        scopedIssues);
  }

  private int persistCutoverBuckets(
      CurrencyInventoryRebuildPreviewVo preview, Instant appliedAt) {
    int count = 0;
    for (CurrencyInventoryAccountPreviewVo account : preview.accounts()) {
      for (CurrencyInventoryBucketPreviewVo source : account.fxBuckets()) {
        if (!ORIGIN_FX.equals(source.originTypeCode()) || source.quantity().signum() <= 0) continue;

        CurrencyInventoryBucket bucket = new CurrencyInventoryBucket();
        bucket.setBucketId(
            UUID.nameUUIDFromBytes(
                    ("fx-v3-cutover|" + account.accountId() + "|" + source.sourceCurrency())
                        .getBytes(StandardCharsets.UTF_8))
                .toString());
        bucket.setAccountId(account.accountId());
        bucket.setCurrency(account.currency());
        bucket.setOriginTypeCode(ORIGIN_FX);
        bucket.setSourceCurrency(source.sourceCurrency());
        bucket.setQuantity(money(source.quantity()));
        bucket.setSourceCostAmount(money(source.sourceCostAmount()));
        bucket.setAverageCostRate(source.averageCostRate().setScale(RATE_SCALE, RoundingMode.HALF_UP));
        bucket.setCreatedAt(appliedAt);
        bucket.setUpdatedAt(appliedAt);
        if (bucketMapper.insert(bucket) != 1) {
          throw new IllegalStateException(
              "FX Cost Inventory bucket insert failed: account="
                  + account.accountId()
                  + ", sourceCurrency="
                  + source.sourceCurrency());
        }
        count++;
      }
    }
    return count;
  }

  private String fingerprint(
      int replayTransactionCount,
      int replayAccountCount,
      List<CurrencyInventoryAccountPreviewVo> accounts,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    StringBuilder canonical = new StringBuilder();
    canonical
        .append("tx=")
        .append(replayTransactionCount)
        .append("|accounts=")
        .append(replayAccountCount)
        .append('\n');
    for (CurrencyInventoryAccountPreviewVo account : accounts) {
      canonical
          .append("A|")
          .append(account.accountId())
          .append('|')
          .append(account.currency())
          .append('|')
          .append(account.accountBalance().toPlainString())
          .append('|')
          .append(account.ledgerBalance().toPlainString())
          .append('|')
          .append(account.fxQuantity().toPlainString())
          .append('|')
          .append(account.nonFxQuantity().toPlainString())
          .append('\n');
      for (CurrencyInventoryBucketPreviewVo bucket : account.fxBuckets()) {
        canonical
            .append("B|")
            .append(bucket.originTypeCode())
            .append('|')
            .append(bucket.sourceCurrency())
            .append('|')
            .append(bucket.quantity().toPlainString())
            .append('|')
            .append(bucket.sourceCostAmount().toPlainString())
            .append('|')
            .append(bucket.averageCostRate().toPlainString())
            .append('\n');
      }
    }
    issues.stream()
        .sorted(
            Comparator.comparing(
                    CurrencyInventoryRebuildIssueVo::transactionId,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(
                    CurrencyInventoryRebuildIssueVo::accountId,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(CurrencyInventoryRebuildIssueVo::code)
                .thenComparing(CurrencyInventoryRebuildIssueVo::message))
        .forEach(
            issue ->
                canonical
                    .append("I|")
                    .append(issue.transactionId())
                    .append('|')
                    .append(issue.accountId())
                    .append('|')
                    .append(issue.code())
                    .append('|')
                    .append(issue.message())
                    .append('\n'));

    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private void replayTransactions(
      List<TransactionReplay> transactions,
      Map<String, AccountState> states,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    for (TransactionReplay transaction : transactions) {
      replayTransaction(transaction, states, issues);
    }
  }

  private List<TransactionReplay> groupTransactions(List<CurrencyInventoryReplayRow> rows) {
    if (rows == null || rows.isEmpty()) return List.of();
    Map<String, List<CurrencyInventoryReplayRow>> grouped = new LinkedHashMap<>();
    for (CurrencyInventoryReplayRow row : rows) {
      grouped.computeIfAbsent(row.getTransactionId(), ignored -> new ArrayList<>()).add(row);
    }

    List<TransactionReplay> result = new ArrayList<>();
    for (Map.Entry<String, List<CurrencyInventoryReplayRow>> entry : grouped.entrySet()) {
      List<CurrencyInventoryReplayRow> transactionRows = entry.getValue();
      transactionRows.sort(
          Comparator.comparing(
                  CurrencyInventoryReplayRow::getEntryCreatedAt,
                  Comparator.nullsLast(Comparator.naturalOrder()))
              .thenComparing(
                  CurrencyInventoryReplayRow::getEntryId,
                  Comparator.nullsLast(Comparator.naturalOrder())));
      result.add(new TransactionReplay(entry.getKey(), transactionRows.getFirst(), transactionRows));
    }
    result.sort(TRANSACTION_REPLAY_ORDER);
    return result;
  }

  private void replayTransaction(
      TransactionReplay transaction,
      Map<String, AccountState> states,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    String type = norm(transaction.first().getTransactionType());
    if ("EXCHANGE".equals(type)) {
      replayExchange(transaction, states, issues);
    } else if ("TRANSFER".equals(type)) {
      replayTransfer(transaction, states, issues);
    } else {
      replayGeneric(transaction, states, issues);
    }
  }

  /**
   * A real FX transaction always creates a direct target cost bucket from the actual source
   * currency paid. The source account's own older FX buckets are only relevant to the source
   * account's remaining cost analysis; they do not redefine the target's direct source currency.
   */
  private void replayExchange(
      TransactionReplay transaction,
      Map<String, AccountState> states,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    String transactionId = transaction.transactionId();
    List<CurrencyInventoryReplayRow> rows = transaction.rows();

    // Pre-cutover FX history is only reconstructed for accounts that have a current legacy V2
    // position. If neither side is migration-eligible, this exchange is still a valid historical
    // transaction fact, but it cannot create a new V3 current-cost bucket during migration.
    boolean touchesEligibleAccount =
        rows.stream()
            .map(CurrencyInventoryReplayRow::getAccountId)
            .map(states::get)
            .anyMatch(state -> state != null && state.legacyFxEligible);
    if (!touchesEligibleAccount) {
      applyLedgerDeltas(rows, states);
      return;
    }

    FxDetail fx = fxDetailMapper.selectById(transactionId);
    if (fx == null) {
      issue(issues, transactionId, null, "FX_DETAIL_MISSING", "EXCHANGE 缺少 b_fx_details，无法恢复直接 FX 成本");
      replayGeneric(transaction, states, issues);
      return;
    }

    AccountState source = states.get(fx.getSourceAccountId());
    AccountState target = states.get(fx.getTargetAccountId());
    String sourceCurrency = norm(fx.getSourceCurrency());
    String targetCurrency = norm(fx.getTargetCurrency());
    if (source == null
        || target == null
        || !source.currency.equals(sourceCurrency)
        || !target.currency.equals(targetCurrency)
        || sourceCurrency.equals(targetCurrency)) {
      issue(
          issues,
          transactionId,
          source == null ? fx.getSourceAccountId() : source.accountId,
          "FX_ACCOUNT_SHAPE_MISMATCH",
          "FxDetail 的来源/目标账户或币种与 ASSET Account 不一致");
      replayGeneric(transaction, states, issues);
      return;
    }

    BigDecimal sourceAmount = money(fx.getSourceAmount());
    BigDecimal targetAmount = money(fx.getTargetAmount());
    if (sourceAmount.signum() <= 0 || targetAmount.signum() <= 0) {
      issue(issues, transactionId, source.accountId, "FX_AMOUNT_INVALID", "FxDetail sourceAmount/targetAmount 必须大于 0");
      replayGeneric(transaction, states, issues);
      return;
    }

    BigDecimal sourceOutflow = sourceAmount;
    BigDecimal targetNet = targetAmount;
    BigDecimal fee =
        fx.getFeeAmount() == null
            ? BigDecimal.ZERO.setScale(MONEY_SCALE)
            : money(fx.getFeeAmount());
    if (fee.signum() > 0) {
      String feeCurrency = norm(fx.getFeeCurrency());
      if (sourceCurrency.equals(feeCurrency)) {
        sourceOutflow = money(sourceOutflow.add(fee));
      } else if (targetCurrency.equals(feeCurrency)) {
        targetNet = money(targetNet.subtract(fee));
      } else {
        issue(
            issues,
            transactionId,
            source.accountId,
            "FX_FEE_CURRENCY_UNSUPPORTED",
            "FX 手续费币种既不是来源币种也不是目标币种，无法安全恢复直接成本");
        replayGeneric(transaction, states, issues);
        return;
      }
    }
    if (targetNet.signum() <= 0) {
      issue(issues, transactionId, target.accountId, "FX_TARGET_NET_INVALID", "扣除目标币种手续费后实际入账数量必须大于 0");
      replayGeneric(transaction, states, issues);
      return;
    }

    BigDecimal sourceLedgerDelta = sumAmount(rows, source.accountId);
    BigDecimal targetLedgerDelta = sumAmount(rows, target.accountId);
    if (sourceLedgerDelta.compareTo(sourceOutflow.negate()) != 0
        || targetLedgerDelta.compareTo(targetNet) != 0
        || distinctAccountIds(rows).size() != 2) {
      issue(
          issues,
          transactionId,
          source.accountId,
          "FX_DETAIL_ENTRY_MISMATCH",
          "FxDetail 金额与 TransactionEntry 净额不一致，不能把 FxDetail 当作直接成本事实");
      replayGeneric(transaction, states, issues);
      return;
    }

    // Source-side FX buckets are only reconstructed/depleted when legacy V2 was still tracking
    // that source account at cutover.
    if (source.legacyFxEligible) {
      depleteFxForOutflow(
          source, sourceOutflow, source.replayBalance, transactionId, "EXCHANGE", issues);
    }

    // Target-side direct cost is the actual source currency paid for this exchange. Historical V3
    // migration only creates it for accounts that have a positive legacy position at cutover. This
    // intentionally avoids inventing reverse JPY (or other) current-cost buckets that V2 never
    // maintained and whose remaining quantity cannot be recovered from damaged legacy ordering.
    if (target.legacyFxEligible) {
      acquireFx(target, sourceCurrency, targetNet, sourceOutflow);
    }

    applyLedgerDeltas(rows, states);
    validateAffectedFxCoverage(transactionId, rows, states, issues);
  }

  /** Same-currency transfers carry only the FX-cost portion; the non-FX residual remains implicit. */
  private void replayTransfer(
      TransactionReplay transaction,
      Map<String, AccountState> states,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    String transactionId = transaction.transactionId();
    List<CurrencyInventoryReplayRow> rows = transaction.rows();
    List<CurrencyInventoryReplayRow> negatives =
        rows.stream().filter(row -> row.getAmount() != null && row.getAmount().signum() < 0).toList();
    List<CurrencyInventoryReplayRow> positives =
        rows.stream().filter(row -> row.getAmount() != null && row.getAmount().signum() > 0).toList();
    if (negatives.size() != 1 || positives.size() != 1) {
      issue(issues, transactionId, null, "TRANSFER_SHAPE_UNSUPPORTED", "TRANSFER 不是单一 ASSET source + target");
      replayGeneric(transaction, states, issues);
      return;
    }

    CurrencyInventoryReplayRow sourceRow = negatives.getFirst();
    CurrencyInventoryReplayRow targetRow = positives.getFirst();
    AccountState source = states.get(sourceRow.getAccountId());
    AccountState target = states.get(targetRow.getAccountId());
    BigDecimal quantity = money(sourceRow.getAmount().abs());
    if (source == null
        || target == null
        || !source.currency.equals(target.currency)
        || quantity.compareTo(money(targetRow.getAmount())) != 0) {
      issue(
          issues,
          transactionId,
          sourceRow.getAccountId(),
          "TRANSFER_ACCOUNT_MISMATCH",
          "同币种 TRANSFER 的账户币种/金额不一致");
      replayGeneric(transaction, states, issues);
      return;
    }

    List<DisposedPortion> movedFx =
        source.legacyFxEligible
            ? depleteFxForOutflow(
                source, quantity, source.replayBalance, transactionId, "TRANSFER", issues)
            : List.of();
    if (target.legacyFxEligible) {
      for (DisposedPortion portion : movedFx) acquirePortion(target, portion);
    }

    applyLedgerDeltas(rows, states);
    validateAffectedFxCoverage(transactionId, rows, states, issues);
  }

  /** Ordinary cash-flow only changes FX buckets when it disposes part of an account that has FX cost. */
  private void replayGeneric(
      TransactionReplay transaction,
      Map<String, AccountState> states,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    Map<String, BigDecimal> deltas = netDeltas(transaction.rows());
    for (Map.Entry<String, BigDecimal> entry : deltas.entrySet()) {
      AccountState state = states.get(entry.getKey());
      if (state == null) continue;
      BigDecimal delta = money(entry.getValue());
      if (delta.signum() < 0 && state.legacyFxEligible && state.fxQuantity().signum() > 0) {
        depleteFxForOutflow(
            state,
            delta.abs(),
            state.replayBalance,
            transaction.transactionId(),
            "ORDINARY_DISPOSAL",
            issues);
      }
    }
    applyLedgerDeltas(transaction.rows(), states);
    validateAffectedFxCoverage(transaction.transactionId(), transaction.rows(), states, issues);
  }

  /**
   * Depletes FX buckets as their pro-rata share of a full account outflow. The remainder of the
   * account balance is an implicit non-FX component and participates in the same allocation.
   *
   * <p>If historical ordering makes the pre-transaction account balance impossible while FX cost
   * already exists, the exact remaining FX quantity cannot be reconstructed safely. That is an FX
   * migration blocker. The same impossible old ordering on an account with zero FX quantity is not
   * an FX blocker and is deliberately ignored by this preview.
   */
  private List<DisposedPortion> depleteFxForOutflow(
      AccountState state,
      BigDecimal requestedQuantity,
      BigDecimal preBalance,
      String transactionId,
      String context,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    BigDecimal requested = money(requestedQuantity);
    if (requested.signum() <= 0 || state.fxQuantity().signum() <= 0) return List.of();

    BigDecimal balance = money(preBalance);
    BigDecimal fxQuantity = state.fxQuantity();
    if (balance.signum() <= 0
        || fxQuantity.compareTo(balance) > 0
        || requested.compareTo(balance) > 0) {
      issue(
          issues,
          transactionId,
          state.accountId,
          "FX_DEPLETION_BASIS_UNAVAILABLE",
          context
              + " 需要从 "
              + state.currency
              + " 账户出库 "
              + requested
              + "，但旧历史顺序下交易前余额="
              + balance
              + "、FX成本数量="
              + fxQuantity
              + "；无法可靠计算 pro-rata FX 成本结转");
      return List.of();
    }

    BigDecimal nonFxQuantity = money(balance.subtract(fxQuantity));
    List<AllocationComponent> components = new ArrayList<>();
    List<BucketState> active =
        state.buckets.values().stream()
            .filter(bucket -> bucket.quantity.signum() > 0)
            .sorted(Comparator.comparing(BucketState::stableKey))
            .toList();
    for (BucketState bucket : active) {
      components.add(new AllocationComponent(bucket.stableKey(), bucket.quantity, bucket));
    }
    if (nonFxQuantity.signum() > 0) {
      components.add(new AllocationComponent("~NON_FX", nonFxQuantity, null));
    }

    Map<AllocationComponent, BigDecimal> allocations = allocateProRata(components, requested, balance);
    List<DisposedPortion> portions = new ArrayList<>();
    for (AllocationComponent component : components) {
      BucketState bucket = component.bucket();
      if (bucket == null) continue;
      BigDecimal allocated =
          allocations.getOrDefault(component, BigDecimal.ZERO.setScale(MONEY_SCALE));
      if (allocated.signum() <= 0) continue;

      BigDecimal cost;
      if (allocated.compareTo(bucket.quantity) == 0) {
        cost = money(bucket.sourceCost);
      } else {
        cost =
            bucket.sourceCost
                .multiply(allocated)
                .divide(bucket.quantity, MONEY_SCALE, RoundingMode.HALF_UP);
      }

      BigDecimal remainingQuantity = money(bucket.quantity.subtract(allocated));
      if (remainingQuantity.signum() == 0) {
        state.buckets.remove(bucket.sourceCurrency);
      } else {
        bucket.quantity = remainingQuantity;
        bucket.sourceCost = money(bucket.sourceCost.subtract(cost));
      }
      portions.add(new DisposedPortion(bucket.sourceCurrency, allocated, cost));
    }
    return portions;
  }

  private Map<AllocationComponent, BigDecimal> allocateProRata(
      List<AllocationComponent> components, BigDecimal consume, BigDecimal total) {
    BigInteger consumeUnits = units(consume);
    BigInteger totalUnits = units(total);
    List<UnitAllocation> allocations = new ArrayList<>();
    BigInteger allocatedUnits = BigInteger.ZERO;

    for (AllocationComponent component : components) {
      BigInteger componentUnits = units(component.quantity());
      BigInteger numerator = consumeUnits.multiply(componentUnits);
      BigInteger[] qr = numerator.divideAndRemainder(totalUnits);
      allocations.add(new UnitAllocation(component, qr[0], qr[1]));
      allocatedUnits = allocatedUnits.add(qr[0]);
    }

    BigInteger remaining = consumeUnits.subtract(allocatedUnits);
    allocations.sort(
        Comparator.comparing(UnitAllocation::remainder)
            .reversed()
            .thenComparing(item -> item.component().stableKey()));
    int extraUnits = remaining.intValueExact();
    for (int i = 0; i < extraUnits; i++) {
      UnitAllocation item = allocations.get(i);
      item.units = item.units.add(BigInteger.ONE);
    }

    Map<AllocationComponent, BigDecimal> result = new LinkedHashMap<>();
    allocations.stream()
        .sorted(Comparator.comparing(item -> item.component().stableKey()))
        .forEach(item -> result.put(item.component(), fromUnits(item.units)));
    return result;
  }

  private void acquireFx(
      AccountState state,
      String sourceCurrency,
      BigDecimal quantity,
      BigDecimal sourceCostAmount) {
    String source = norm(sourceCurrency);
    BucketState bucket =
        state.buckets.computeIfAbsent(source, ignored -> new BucketState(source));
    bucket.quantity = money(bucket.quantity.add(quantity));
    bucket.sourceCost = money(bucket.sourceCost.add(sourceCostAmount));
  }

  private void acquirePortion(AccountState state, DisposedPortion portion) {
    acquireFx(state, portion.sourceCurrency(), portion.quantity(), portion.sourceCost());
  }

  private void applyLedgerDeltas(
      List<CurrencyInventoryReplayRow> rows, Map<String, AccountState> states) {
    Map<String, BigDecimal> deltas = netDeltas(rows);
    for (Map.Entry<String, BigDecimal> entry : deltas.entrySet()) {
      AccountState state = states.get(entry.getKey());
      if (state != null) state.replayBalance = money(state.replayBalance.add(entry.getValue()));
    }
  }

  private Map<String, BigDecimal> netDeltas(List<CurrencyInventoryReplayRow> rows) {
    Map<String, BigDecimal> deltas = new LinkedHashMap<>();
    for (CurrencyInventoryReplayRow row : rows) {
      if (row.getAccountId() == null || row.getAmount() == null) continue;
      deltas.merge(row.getAccountId(), row.getAmount(), BigDecimal::add);
    }
    return deltas;
  }

  /**
   * Only an impossible balance/FX relationship is an FX replay issue. A historical negative running
   * balance on an account with no FX bucket is outside this preview's scope and does not block FX
   * migration.
   */
  private void validateAffectedFxCoverage(
      String transactionId,
      List<CurrencyInventoryReplayRow> rows,
      Map<String, AccountState> states,
      List<CurrencyInventoryRebuildIssueVo> issues) {
    for (String accountId : distinctAccountIds(rows)) {
      AccountState state = states.get(accountId);
      if (state == null || !state.legacyFxEligible || state.fxQuantity().signum() <= 0) continue;
      if (state.replayBalance.signum() < 0 || state.fxQuantity().compareTo(state.replayBalance) > 0) {
        issue(
            issues,
            transactionId,
            state.accountId,
            "FX_REPLAY_BALANCE_AMBIGUOUS",
            "旧历史顺序下余额="
                + state.replayBalance
                + " "
                + state.currency
                + "，但当前已重建 FX 成本数量="
                + state.fxQuantity()
                + "；无法确认该顺序下的 FX 成本结转");
      }
    }
  }

  private void addFinalInvariantIssues(
      Map<String, AccountState> states, List<CurrencyInventoryRebuildIssueVo> issues) {
    for (AccountState state : states.values()) {
      if (!state.legacyFxEligible) continue;
      if (state.accountBalance.compareTo(state.ledgerBalance) != 0) {
        issue(
            issues,
            null,
            state.accountId,
            "ACCOUNT_LEDGER_MISMATCH",
            "Account.totalAmount="
                + state.accountBalance
                + "，TransactionEntry SUM="
                + state.ledgerBalance);
      }
      if (state.accountBalance.signum() < 0 || state.ledgerBalance.signum() < 0) {
        issue(
            issues,
            null,
            state.accountId,
            "NEGATIVE_ASSET_BALANCE",
            "ASSET 账户当前余额不能为负：Account="
                + state.accountBalance
                + "，Ledger="
                + state.ledgerBalance);
      }

      BigDecimal fxQuantity = state.fxQuantity();
      if (fxQuantity.signum() < 0) {
        issue(issues, null, state.accountId, "FX_QUANTITY_NEGATIVE", "FX 成本数量不能为负：" + fxQuantity);
      }
      if (state.accountBalance.signum() >= 0 && fxQuantity.compareTo(state.accountBalance) > 0) {
        issue(
            issues,
            null,
            state.accountId,
            "FX_QUANTITY_EXCEEDS_ACCOUNT_BALANCE",
            "FX 成本数量=" + fxQuantity + "，超过账户当前余额=" + state.accountBalance);
      }
      for (BucketState bucket : state.buckets.values()) {
        if (bucket.sourceCost.signum() < 0) {
          issue(
              issues,
              null,
              state.accountId,
              "FX_SOURCE_COST_NEGATIVE",
              bucket.sourceCurrency + " 来源成本不能为负：" + bucket.sourceCost);
        }
      }
    }
  }

  private CurrencyInventoryAccountPreviewVo toAccountPreview(
      AccountState state, List<CurrencyInventoryRebuildIssueVo> allIssues) {
    BigDecimal fxQuantity = state.fxQuantity();
    BigDecimal nonFxQuantity = money(state.accountBalance.subtract(fxQuantity));
    List<CurrencyInventoryBucketPreviewVo> buckets =
        state.buckets.values().stream()
            .filter(bucket -> bucket.quantity.signum() > 0)
            .sorted(Comparator.comparing(BucketState::stableKey))
            .map(
                bucket ->
                    new CurrencyInventoryBucketPreviewVo(
                        ORIGIN_FX,
                        bucket.sourceCurrency,
                        money(bucket.quantity),
                        money(bucket.sourceCost),
                        average(bucket.sourceCost, bucket.quantity)))
            .toList();
    List<CurrencyInventoryRebuildIssueVo> accountIssues =
        allIssues.stream().filter(issue -> state.accountId.equals(issue.accountId())).toList();
    return new CurrencyInventoryAccountPreviewVo(
        state.accountId,
        state.accountName,
        state.currency,
        state.accountBalance,
        state.ledgerBalance,
        fxQuantity,
        nonFxQuantity,
        money(state.accountBalance.subtract(state.ledgerBalance)),
        state.accountBalance.compareTo(state.ledgerBalance) == 0,
        state.accountBalance.signum() >= 0 && fxQuantity.compareTo(state.accountBalance) <= 0,
        buckets,
        accountIssues);
  }

  private Set<String> legacyFxAccountIds(Map<String, AccountState> states) {
    List<String> raw = rebuildMapper.selectLegacyFxPositionAccountIds();
    if (raw == null || raw.isEmpty()) return Set.of();
    Set<String> result = new LinkedHashSet<>();
    for (String accountId : raw) {
      if (accountId != null && states.containsKey(accountId)) result.add(accountId);
    }
    return result;
  }

  /**
   * Scoped preview must include accounts that can carry FX cost through EXCHANGE/TRANSFER into the
   * requested accounts, while excluding unrelated accounts from issue/apply status.
   */
  private Set<String> expandFxDependencyScope(
      Set<String> requested, List<TransactionReplay> transactions) {
    Set<String> scope = new LinkedHashSet<>(requested);
    boolean changed;
    do {
      changed = false;
      for (TransactionReplay transaction : transactions) {
        String type = norm(transaction.first().getTransactionType());
        if (!"EXCHANGE".equals(type) && !"TRANSFER".equals(type)) continue;
        Set<String> accounts = distinctAccountIds(transaction.rows());
        boolean intersects = accounts.stream().anyMatch(scope::contains);
        if (intersects && scope.addAll(accounts)) changed = true;
      }
    } while (changed);
    return scope;
  }

  private List<CurrencyInventoryRebuildIssueVo> scopeIssues(
      List<CurrencyInventoryRebuildIssueVo> issues,
      Set<String> accountScope,
      List<TransactionReplay> transactions) {
    Map<String, Set<String>> transactionAccounts = new LinkedHashMap<>();
    for (TransactionReplay transaction : transactions) {
      transactionAccounts.put(transaction.transactionId(), distinctAccountIds(transaction.rows()));
    }

    return issues.stream()
        .filter(
            issue -> {
              if (issue.accountId() != null && accountScope.contains(issue.accountId())) return true;
              if (issue.transactionId() == null) return false;
              Set<String> accounts = transactionAccounts.get(issue.transactionId());
              return accounts != null && accounts.stream().anyMatch(accountScope::contains);
            })
        .toList();
  }

  private int countTransactionsTouchingScope(
      List<TransactionReplay> transactions, Set<String> accountScope) {
    int count = 0;
    for (TransactionReplay transaction : transactions) {
      if (distinctAccountIds(transaction.rows()).stream().anyMatch(accountScope::contains)) count++;
    }
    return count;
  }

  private Set<String> normalizeRequestedIds(CurrencyInventoryRebuildRequestVo request) {
    if (request == null || request.accountIds() == null || request.accountIds().isEmpty()) {
      return Set.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    for (String id : request.accountIds()) {
      if (id != null && !id.isBlank()) ids.add(id.trim());
    }
    return ids;
  }

  private Set<String> distinctAccountIds(List<CurrencyInventoryReplayRow> rows) {
    Set<String> ids = new HashSet<>();
    for (CurrencyInventoryReplayRow row : rows) {
      if (row.getAccountId() != null) ids.add(row.getAccountId());
    }
    return ids;
  }

  private BigDecimal sumAmount(List<CurrencyInventoryReplayRow> rows, String accountId) {
    BigDecimal total = BigDecimal.ZERO;
    for (CurrencyInventoryReplayRow row : rows) {
      if (accountId.equals(row.getAccountId()) && row.getAmount() != null) total = total.add(row.getAmount());
    }
    return money(total);
  }

  private BigDecimal average(BigDecimal cost, BigDecimal quantity) {
    if (quantity == null || quantity.signum() == 0) return BigDecimal.ZERO.setScale(RATE_SCALE);
    return cost.divide(quantity, RATE_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal money(BigDecimal value) {
    if (value == null) return BigDecimal.ZERO.setScale(MONEY_SCALE);
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigInteger units(BigDecimal value) {
    return money(value).movePointRight(MONEY_SCALE).toBigIntegerExact();
  }

  private BigDecimal fromUnits(BigInteger units) {
    return new BigDecimal(units).movePointLeft(MONEY_SCALE).setScale(MONEY_SCALE);
  }

  private String norm(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private void issue(
      List<CurrencyInventoryRebuildIssueVo> issues,
      String transactionId,
      String accountId,
      String code,
      String message) {
    // Avoid flooding the preview with the exact same blocker for the same transaction/account.
    boolean exists =
        issues.stream()
            .anyMatch(
                current ->
                    java.util.Objects.equals(current.transactionId(), transactionId)
                        && java.util.Objects.equals(current.accountId(), accountId)
                        && current.code().equals(code));
    if (!exists) issues.add(new CurrencyInventoryRebuildIssueVo(transactionId, accountId, code, message));
  }

  private static final Comparator<TransactionReplay> TRANSACTION_REPLAY_ORDER =
      Comparator.comparing(
              (TransactionReplay tx) -> tx.first().getTransactionTime(),
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              tx -> tx.first().getTransactionCreatedAt(),
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(TransactionReplay::transactionId, Comparator.nullsLast(String::compareTo));

  private static final class AccountState {
    private final String accountId;
    private final String accountName;
    private final String currency;
    private final BigDecimal accountBalance;
    private final BigDecimal ledgerBalance;
    private boolean legacyFxEligible;
    private BigDecimal replayBalance = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private final Map<String, BucketState> buckets = new LinkedHashMap<>();

    private AccountState(CurrencyInventoryAccountSnapshot snapshot) {
      accountId = snapshot.getAccountId();
      accountName = snapshot.getAccountName();
      currency = snapshot.getCurrency() == null ? "" : snapshot.getCurrency().trim().toUpperCase(Locale.ROOT);
      accountBalance =
          snapshot.getAccountBalance() == null
              ? BigDecimal.ZERO.setScale(MONEY_SCALE)
              : snapshot.getAccountBalance().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      ledgerBalance =
          snapshot.getLedgerBalance() == null
              ? BigDecimal.ZERO.setScale(MONEY_SCALE)
              : snapshot.getLedgerBalance().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal fxQuantity() {
      BigDecimal total = BigDecimal.ZERO;
      for (BucketState bucket : buckets.values()) total = total.add(bucket.quantity);
      return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
  }

  private static final class BucketState {
    private final String sourceCurrency;
    private BigDecimal quantity = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private BigDecimal sourceCost = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private BucketState(String sourceCurrency) {
      this.sourceCurrency = sourceCurrency;
    }

    private String stableKey() {
      return sourceCurrency;
    }
  }

  private record TransactionReplay(
      String transactionId,
      CurrencyInventoryReplayRow first,
      List<CurrencyInventoryReplayRow> rows) {}

  private record DisposedPortion(
      String sourceCurrency, BigDecimal quantity, BigDecimal sourceCost) {}

  private record AllocationComponent(String stableKey, BigDecimal quantity, BucketState bucket) {}

  private static final class UnitAllocation {
    private final AllocationComponent component;
    private BigInteger units;
    private final BigInteger remainder;

    private UnitAllocation(AllocationComponent component, BigInteger units, BigInteger remainder) {
      this.component = component;
      this.units = units;
      this.remainder = remainder;
    }

    private AllocationComponent component() {
      return component;
    }

    private BigInteger remainder() {
      return remainder;
    }
  }
}
