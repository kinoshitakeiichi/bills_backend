package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanPaymentAllocationResult;
import java.util.List;

public interface LoanPaymentAllocationService {
  LoanPaymentAllocationResult allocate(String loanPaymentTransactionId);
  LoanPaymentAllocationResult allocate(String loanPaymentTransactionId, List<String> preferredScheduleIds);

  /** Removes one payment allocation and rebuilds affected schedule/plan statuses. */
  void reverse(String loanPaymentTransactionId);
}
