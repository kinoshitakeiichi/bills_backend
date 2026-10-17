package com.jiajunxie.service;

import com.jiajunxie.vo.InstallmentPlanVo;
import com.jiajunxie.vo.TransactionCreateVo;
import java.math.BigDecimal;

/** Owns the future-obligation lifecycle for CREDIT_PURCHASE transactions. */
public interface CreditCardPaymentPlanService {
  void validatePayment(TransactionCreateVo transactionVo);
  void createPlan(String transactionId, TransactionCreateVo transactionVo);
  void replacePlan(String transactionId, TransactionCreateVo transactionVo);
  void removePlan(String transactionId);
  boolean isLocked(String transactionId);
  void assertMutable(String transactionId);
  InstallmentPlanVo getPlan(String transactionId);
  void applyPendingPrincipalRefund(String transactionId, BigDecimal refundAmount);
  void applyPendingPrincipalRefund(
      String transactionId, String refundTransactionId, BigDecimal refundAmount);
  void reversePendingPrincipalRefund(
      String transactionId, String refundTransactionId, BigDecimal refundAmount);
}
