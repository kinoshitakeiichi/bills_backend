package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.CreditCardPaymentPlanService;
import com.jiajunxie.service.CreditPurchaseRefundService;
import com.jiajunxie.vo.CreditPurchaseRefundRequestVo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditPurchaseRefundServiceImpl implements CreditPurchaseRefundService {
  private static final String TYPE_PURCHASE = "CREDIT_PURCHASE";
  private static final String TYPE_REFUND = "CREDIT_REFUND";
  private static final String ORIGIN_REFUND = "CREDIT_REFUND";

  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final TransactionAllocationMapper allocationMapper;
  private final AccountMapper accountMapper;
  private final CreditCardPaymentPlanService paymentPlanService;

  @Override
  @Transactional
  public String refund(String purchaseTransactionId, CreditPurchaseRefundRequestVo request) {
    if (request == null || request.amount() == null || request.amount().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "退款金额必须大于 0");
    }
    Transaction purchase = transactionMapper.selectById(purchaseTransactionId);
    if (purchase == null || !TYPE_PURCHASE.equals(purchase.getTransactionType())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "原信用卡消费不存在");
    }
    List<TransactionEntry> entries = entryMapper.selectByTransactionId(purchaseTransactionId);
    if (entries.size() != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "原信用卡消费分录不完整");
    }
    TransactionEntry purchaseEntry = entries.get(0);
    Account account = accountMapper.selectById(purchaseEntry.getAccountId());
    if (account == null
        || !"LIABILITY".equalsIgnoreCase(account.getAccountClassCode())
        || !"CREDIT_CARD".equalsIgnoreCase(account.getAccountTypeCode())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "原消费账户不是信用卡负债账户");
    }

    String refundId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Transaction refund = new Transaction();
    refund.setTransactionId(refundId);
    refund.setTransactionType(TYPE_REFUND);
    refund.setCategoryId(purchase.getCategoryId());
    refund.setMerchant(purchase.getMerchant());
    refund.setRemark(request.remark());
    refund.setTransactionTime(request.transactionTime() == null ? now : request.transactionTime());
    refund.setOriginCode(ORIGIN_REFUND);
    refund.setOriginReferenceId(purchaseTransactionId);
    refund.setCreatedAt(now);
    refund.setUpdatedAt(now);
    transactionMapper.insert(refund);

    // Keep exact schedule deltas keyed by the refund transaction so a mistaken refund can be
    // reversed without guessing installment history. Any failure rolls back the whole transaction.
    paymentPlanService.applyPendingPrincipalRefund(
        purchaseTransactionId, refundId, request.amount());

    TransactionEntry entry = new TransactionEntry();
    entry.setEntryId(UUID.randomUUID().toString());
    entry.setTransactionId(refundId);
    entry.setAccountId(account.getAccountId());
    entry.setAmount(request.amount().negate());
    entry.setCurrency(purchaseEntry.getCurrency());
    entry.setEntryRole("SINGLE");
    entry.setCreatedAt(now);
    entryMapper.insert(entry);

    if (accountMapper.addBalance(account.getAccountId(), request.amount().negate()) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡退款负债更新失败");
    }

    List<TransactionAllocation> originalAllocations =
        allocationMapper.selectList(
            Wrappers.<TransactionAllocation>lambdaQuery()
                .eq(TransactionAllocation::getTransactionId, purchaseTransactionId));
    applyRefundAllocations(
        refundId,
        purchaseEntry.getCurrency(),
        request.amount(),
        originalAllocations,
        now);
    return refundId;
  }
  @Override
  @Transactional
  public void reverse(String refundTransactionId) {
    if (refundTransactionId == null || refundTransactionId.isBlank()) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "退款交易 ID 不能为空");
    }
    Transaction refund = transactionMapper.selectById(refundTransactionId);
    if (refund == null || !TYPE_REFUND.equals(refund.getTransactionType())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "信用卡退款交易不存在");
    }
    if (!ORIGIN_REFUND.equals(refund.getOriginCode())
        || refund.getOriginReferenceId() == null
        || refund.getOriginReferenceId().isBlank()) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该交易不是可撤销的信用卡消费退款");
    }

    List<TransactionEntry> entries = entryMapper.selectByTransactionId(refundTransactionId);
    if (entries.size() != 1 || entries.get(0).getAmount() == null || entries.get(0).getAmount().signum() >= 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡退款分录不完整");
    }
    TransactionEntry refundEntry = entries.get(0);
    BigDecimal refundAmount = refundEntry.getAmount().abs();
    Account account = accountMapper.selectById(refundEntry.getAccountId());
    if (account == null
        || !"LIABILITY".equalsIgnoreCase(account.getAccountClassCode())
        || !"CREDIT_CARD".equalsIgnoreCase(account.getAccountTypeCode())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "退款账户不是有效信用卡负债账户");
    }

    paymentPlanService.reversePendingPrincipalRefund(
        refund.getOriginReferenceId(), refundTransactionId, refundAmount);

    if (accountMapper.addBalance(account.getAccountId(), refundAmount) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡退款撤销负债恢复失败");
    }
    allocationMapper.deleteByTransactionId(refundTransactionId);
    entryMapper.deleteByTransactionId(refundTransactionId);
    if (transactionMapper.deleteById(refundTransactionId) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "信用卡退款撤销失败");
    }
  }

  private void applyRefundAllocations(
      String refundTransactionId,
      String currency,
      BigDecimal refundAmount,
      List<TransactionAllocation> originalAllocations,
      Instant now) {
    if (originalAllocations == null || originalAllocations.isEmpty()) {
      return;
    }

    BigDecimal originalTotal =
        originalAllocations.stream()
            .map(TransactionAllocation::getAllocatedAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (originalTotal.signum() <= 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "原信用卡消费的人员分摊金额无效，无法生成退款分摊");
    }

    BigDecimal allocatedRefund = BigDecimal.ZERO;
    for (int index = 0; index < originalAllocations.size(); index++) {
      TransactionAllocation original = originalAllocations.get(index);
      BigDecimal originalAmount = original.getAllocatedAmount();
      if (originalAmount == null || originalAmount.signum() <= 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT, "原信用卡消费存在无效人员分摊，无法生成退款分摊");
      }

      BigDecimal amount;
      if (index == originalAllocations.size() - 1) {
        amount = refundAmount.subtract(allocatedRefund);
      } else {
        amount =
            refundAmount
                .multiply(originalAmount)
                .divide(originalTotal, 4, RoundingMode.HALF_UP);
        allocatedRefund = allocatedRefund.add(amount);
      }

      TransactionAllocation allocation = new TransactionAllocation();
      allocation.setAllocationId(UUID.randomUUID().toString());
      allocation.setTransactionId(refundTransactionId);
      allocation.setPersonId(original.getPersonId());
      allocation.setAllocatedAmount(amount);
      allocation.setCurrency(currency);
      // Amount is the authoritative allocation fact. Keep ratio null to avoid precision drift.
      allocation.setAllocationRatio(null);
      allocation.setCreatedAt(now);
      allocationMapper.insert(allocation);
    }
  }

}
