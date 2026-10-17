package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.domain.loan.LoanUnplannedFundingFact;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.LoanScheduleFundingQueryService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanScheduleFundingQueryServiceImpl implements LoanScheduleFundingQueryService {
  private final LoanProfileMapper profileMapper;
  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final InstallmentPlanMapper planMapper;

  @Override
  public List<LoanUnplannedFundingFact> listUnplanned(String loanAccountId) {
    if (loanAccountId == null || loanAccountId.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "贷款账户不能为空");
    }
    LoanProfile profile = profileMapper.selectById(loanAccountId);
    if (profile == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "Loan Profile 不存在");
    }

    String expectedType =
        switch (normalize(profile.getFundingModeCode())) {
          case "ACCOUNT_DISBURSEMENT" -> "LOAN_DISBURSEMENT";
          case "DIRECT_FINANCING", "PURCHASE_FINANCING" -> "LOAN_PURCHASE";
          default ->
              throw new BusinessException(
                  CommonErrorCode.PARAMETER_ERROR, "不支持的贷款资金形成方式");
        };

    Set<String> plannedSources =
        new HashSet<>(
            planMapper
                .selectList(
                    Wrappers.<InstallmentPlan>lambdaQuery()
                        .eq(InstallmentPlan::getLiabilityAccountId, loanAccountId)
                        .eq(InstallmentPlan::getPaymentTypeCode, "LOAN"))
                .stream()
                .map(InstallmentPlan::getSourceTransactionId)
                .toList());

    List<Transaction> transactions =
        transactionMapper.selectList(
            Wrappers.<Transaction>lambdaQuery()
                .eq(Transaction::getOriginReferenceId, loanAccountId)
                .eq(Transaction::getOriginCode, "LOAN_ENGINE")
                .eq(Transaction::getTransactionType, expectedType)
                .orderByAsc(Transaction::getTransactionTime));

    return transactions.stream()
        .filter(tx -> !plannedSources.contains(tx.getTransactionId()))
        .map(
            tx -> {
              List<TransactionEntry> entries =
                  entryMapper.selectList(
                      Wrappers.<TransactionEntry>lambdaQuery()
                          .eq(TransactionEntry::getTransactionId, tx.getTransactionId())
                          .eq(TransactionEntry::getAccountId, loanAccountId));
              if (entries.size() != 1) {
                throw new BusinessException(
                    CommonErrorCode.DATA_CONFLICT,
                    "融资事实必须且只能有一条贷款账户分录");
              }
              TransactionEntry entry = entries.get(0);
              return new LoanUnplannedFundingFact(
                  tx.getTransactionId(),
                  tx.getTransactionType(),
                  tx.getTransactionTime(),
                  entry.getAmount().abs(),
                  entry.getCurrency(),
                  tx.getMerchant(),
                  tx.getRemark());
            })
        .toList();
  }

  private String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }
}
