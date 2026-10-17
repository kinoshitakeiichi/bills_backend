package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.transaction.*;
import com.jiajunxie.entity.*;
import com.jiajunxie.mapper.*;
import com.jiajunxie.model.TransactionLedgerCandidate;
import com.jiajunxie.service.TransactionLedgerQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionLedgerQueryServiceImpl implements TransactionLedgerQueryService {
  private final TransactionLedgerQueryMapper queryMapper;
  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final TransactionAllocationMapper allocationMapper;
  private final AccountMapper accountMapper;
  private final PersonMapper personMapper;
  private final TransactionNatureResolver natureResolver;
  private final TransactionDisplayAmountResolver amountResolver;
  private final TransactionReadonlyResolver readonlyResolver;

  @Override
  @Transactional(readOnly = true)
  public TransactionLedgerPage query(TransactionLedgerQueryCriteria criteria) {
    validate(criteria);

    List<TransactionLedgerCandidate> candidates =
        queryMapper.selectCandidates(
            criteria.startInclusive(),
            criteria.endExclusive(),
            trim(criteria.keyword()),
            criteria.keywordRegex(),
            trim(criteria.accountId()),
            trim(criteria.accountTypeCode()),
            trim(criteria.categoryId()),
            criteria.personIds(),
            criteria.unassignedPerson(),
            trim(criteria.transactionTypeCode()),
            criteria.sort().name());

    if (candidates.isEmpty()) {
      return new TransactionLedgerPage(List.of(), criteria.page(), criteria.pageSize(), 0, 0);
    }

    List<String> ids = candidates.stream().map(TransactionLedgerCandidate::getTransactionId).toList();
    Map<String, Transaction> transactions =
        transactionMapper.selectBatchIds(ids).stream()
            .collect(Collectors.toMap(Transaction::getTransactionId, Function.identity()));

    Map<String, List<TransactionEntry>> entriesByTx =
        entryMapper
            .selectList(Wrappers.<TransactionEntry>lambdaQuery().in(TransactionEntry::getTransactionId, ids))
            .stream()
            .collect(Collectors.groupingBy(TransactionEntry::getTransactionId));

    Map<String, List<TransactionAllocation>> allocationsByTx =
        allocationMapper
            .selectList(
                Wrappers.<TransactionAllocation>lambdaQuery()
                    .in(TransactionAllocation::getTransactionId, ids))
            .stream()
            .collect(Collectors.groupingBy(TransactionAllocation::getTransactionId));

    Set<String> accountIds =
        entriesByTx.values().stream()
            .flatMap(Collection::stream)
            .map(TransactionEntry::getAccountId)
            .collect(Collectors.toSet());
    Map<String, Account> accounts =
        accountIds.isEmpty()
            ? Map.of()
            : accountMapper.selectBatchIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));

    Set<String> personIds =
        allocationsByTx.values().stream()
            .flatMap(Collection::stream)
            .map(TransactionAllocation::getPersonId)
            .collect(Collectors.toSet());
    Map<String, Person> persons =
        personIds.isEmpty()
            ? Map.of()
            : personMapper.selectBatchIds(personIds).stream()
                .collect(Collectors.toMap(Person::getPersonId, Function.identity()));

    List<TransactionLedgerRow> rows =
        candidates.stream()
            .map(
                candidate ->
                    toRow(
                        candidate,
                        transactions.get(candidate.getTransactionId()),
                        entriesByTx.getOrDefault(candidate.getTransactionId(), List.of()),
                        allocationsByTx.getOrDefault(candidate.getTransactionId(), List.of()),
                        accounts,
                        persons))
            .filter(Objects::nonNull)
            .filter(row -> matchesPersonSelection(row, criteria.personIds()))
            .map(row -> applyPersonProjection(row, criteria.personIds()))
            .filter(row -> matchesPostFilters(row, criteria))
            .collect(Collectors.toCollection(ArrayList::new));

    if (criteria.sort() == TransactionLedgerSort.AMOUNT_ASC) {
      rows.sort(
          Comparator.comparing(TransactionLedgerRow::displayAmount)
              .thenComparing(TransactionLedgerRow::transactionTime)
              .thenComparing(TransactionLedgerRow::transactionId));
    } else if (criteria.sort() == TransactionLedgerSort.AMOUNT_DESC) {
      rows.sort(
          Comparator.comparing(TransactionLedgerRow::displayAmount)
              .reversed()
              .thenComparing(TransactionLedgerRow::transactionTime, Comparator.reverseOrder())
              .thenComparing(TransactionLedgerRow::transactionId, Comparator.reverseOrder()));
    }

    long total = rows.size();
    int totalPages = total == 0 ? 0 : (int) ((total + criteria.pageSize() - 1) / criteria.pageSize());
    int from = Math.min((criteria.page() - 1) * criteria.pageSize(), rows.size());
    int to = Math.min(from + criteria.pageSize(), rows.size());

    return new TransactionLedgerPage(
        List.copyOf(rows.subList(from, to)),
        criteria.page(),
        criteria.pageSize(),
        total,
        totalPages);
  }

  private TransactionLedgerRow toRow(
      TransactionLedgerCandidate candidate,
      Transaction transaction,
      List<TransactionEntry> entries,
      List<TransactionAllocation> allocations,
      Map<String, Account> accounts,
      Map<String, Person> persons) {
    if (transaction == null || entries.isEmpty()) return null;

    TransactionNature nature = natureResolver.resolve(transaction.getTransactionType());
    BigDecimal displayAmount = amountResolver.resolve(transaction.getTransactionType(), entries);
    String currency = amountResolver.resolveCurrency(transaction.getTransactionType(), entries);
    String readonlyReason = readonlyResolver.reason(transaction);

    List<TransactionLedgerAccountSummary> accountSummaries =
        entries.stream()
            .map(
                entry -> {
                  Account account = accounts.get(entry.getAccountId());
                  return new TransactionLedgerAccountSummary(
                      entry.getAccountId(),
                      account == null ? null : account.getAccountName(),
                      account == null ? null : account.getAccountCode(),
                      account == null ? null : account.getAccountTypeCode(),
                      account == null ? null : account.getAccountClassCode(),
                      entry.getAmount(),
                      entry.getCurrency(),
                      entry.getEntryRole());
                })
            .toList();

    List<TransactionLedgerPersonAllocation> personAllocations =
        allocations.stream()
            .map(
                allocation -> {
                  Person person = persons.get(allocation.getPersonId());
                  return new TransactionLedgerPersonAllocation(
                      allocation.getPersonId(),
                      person == null ? null : person.getPersonName(),
                      allocation.getAllocatedAmount(),
                      allocation.getCurrency(),
                      allocation.getAllocationRatio());
                })
            .toList();

    return new TransactionLedgerRow(
        candidate.getTransactionId(),
        candidate.getTransactionTime(),
        candidate.getCreatedAt(),
        candidate.getTransactionTypeCode(),
        candidate.getTransactionTypeName(),
        candidate.getOriginCode(),
        candidate.getOriginReferenceId(),
        candidate.getMerchant(),
        candidate.getRemark(),
        candidate.getCategoryId(),
        candidate.getCategoryName(),
        currency,
        displayAmount,
        natureResolver.flowDirection(nature),
        nature,
        candidate.getFxDirectionCode(),
        candidate.getFxFeeAmount(),
        candidate.getFxFeeCurrency(),
        accountSummaries,
        personAllocations,
        readonlyReason != null,
        readonlyReason);
  }


  /**
   * 人员筛选采用“严格归属”语义：
   *
   * <p>单选某人时，只返回 100% 由该人员承担的交易；多选时，只返回恰好由所选人员
   * 共同承担、且分摊金额合计等于交易展示金额的交易。这样不会因为某人只参与了部分分摊
   * 就把整笔交易命中。
   */
  private boolean matchesPersonSelection(
      TransactionLedgerRow row, List<String> personIds) {
    if (personIds == null || personIds.isEmpty()) return true;

    Set<String> selected =
        personIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toSet());
    if (selected.isEmpty()) return true;

    Set<String> actual =
        row.personAllocations().stream()
            .map(TransactionLedgerPersonAllocation::personId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (!actual.equals(selected)) return false;

    BigDecimal allocatedAmount =
        row.personAllocations().stream()
            .map(TransactionLedgerPersonAllocation::allocatedAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal transactionAmount =
        row.displayAmount() == null ? BigDecimal.ZERO : row.displayAmount().abs();

    return allocatedAmount.compareTo(transactionAmount) == 0;
  }


  private TransactionLedgerRow applyPersonProjection(
      TransactionLedgerRow row, List<String> personIds) {
    if (personIds == null || personIds.isEmpty()) return row;

    Set<String> selected = new HashSet<>(personIds);
    List<TransactionLedgerPersonAllocation> allocations =
        row.personAllocations().stream()
            .filter(item -> selected.contains(item.personId()))
            .toList();
    if (allocations.isEmpty()) return row;

    BigDecimal allocatedAmount =
        allocations.stream()
            .map(TransactionLedgerPersonAllocation::allocatedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    String allocationCurrency =
        allocations.stream()
            .map(TransactionLedgerPersonAllocation::currency)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(row.currency());

    return new TransactionLedgerRow(
        row.transactionId(),
        row.transactionTime(),
        row.createdAt(),
        row.transactionTypeCode(),
        row.transactionTypeName(),
        row.originCode(),
        row.originReferenceId(),
        row.merchant(),
        row.remark(),
        row.categoryId(),
        row.categoryName(),
        allocationCurrency,
        allocatedAmount,
        row.flowDirection(),
        row.transactionNature(),
        row.fxDirectionCode(),
        row.fxFeeAmount(),
        row.fxFeeCurrency(),
        row.accountSummaries(),
        allocations,
        row.readonly(),
        row.readonlyReason());
  }

  boolean matchesRegex(TransactionLedgerRow row, Pattern pattern) {
    if (pattern == null) return true;

    StringJoiner searchDocument = new StringJoiner(" ");
    addRegexSearchValue(searchDocument, row.merchant());
    addRegexSearchValue(searchDocument, row.remark());
    addRegexSearchValue(searchDocument, row.categoryName());
    addRegexSearchValue(searchDocument, row.transactionTypeName());
    addRegexSearchValue(searchDocument, row.transactionTypeCode());
    row.accountSummaries().forEach(account -> {
      addRegexSearchValue(searchDocument, account.accountName());
      addRegexSearchValue(searchDocument, account.accountCode());
    });
    row.personAllocations()
        .forEach(allocation -> addRegexSearchValue(searchDocument, allocation.personName()));

    return pattern.matcher(searchDocument.toString()).find();
  }

  private void addRegexSearchValue(StringJoiner searchDocument, String value) {
    if (value == null || value.isBlank()) return;
    searchDocument.add(value.trim().replaceAll("\\s+", " "));
  }

  private boolean matchesPostFilters(
      TransactionLedgerRow row, TransactionLedgerQueryCriteria criteria) {
    if (criteria.unassignedPerson() && criteria.personIds() != null && !criteria.personIds().isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.PARAMETER_ERROR, "未分配人员筛选不能与指定人员同时使用");
    }
    if (criteria.keywordRegex() && trim(criteria.keyword()) != null) {
      Pattern pattern = Pattern.compile(criteria.keyword(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      if (!matchesRegex(row, pattern)) return false;
    }
    if (criteria.nature() != null && row.transactionNature() != criteria.nature()) return false;
    if (trim(criteria.currency()) != null
        && !criteria.currency().trim().equalsIgnoreCase(row.currency())) return false;
    if (criteria.minAmount() != null && row.displayAmount().compareTo(criteria.minAmount()) < 0)
      return false;
    if (criteria.maxAmount() != null && row.displayAmount().compareTo(criteria.maxAmount()) > 0)
      return false;
    if (criteria.readonly() != null && row.readonly() != criteria.readonly()) return false;
    return true;
  }

  private void validate(TransactionLedgerQueryCriteria criteria) {
    if (criteria == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账单查询条件不能为空");
    }
    if (criteria.startInclusive() == null || criteria.endExclusive() == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账单查询必须指定时间范围");
    }
    if (!criteria.startInclusive().isBefore(criteria.endExclusive())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "账单查询开始时间必须早于结束时间");
    }
    if (criteria.keywordRegex() && trim(criteria.keyword()) != null) {
      try {
        Pattern.compile(criteria.keyword(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      } catch (PatternSyntaxException exception) {
        throw new BusinessException(
            CommonErrorCode.PARAMETER_ERROR, "正则表达式无效：" + exception.getDescription());
      }
    }
    if (criteria.minAmount() != null && criteria.minAmount().signum() < 0
        || criteria.maxAmount() != null && criteria.maxAmount().signum() < 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "金额筛选不能为负数");
    }
    if (criteria.minAmount() != null
        && criteria.maxAmount() != null
        && criteria.minAmount().compareTo(criteria.maxAmount()) > 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "最小金额不能大于最大金额");
    }
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
