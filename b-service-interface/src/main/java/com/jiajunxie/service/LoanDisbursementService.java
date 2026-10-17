package com.jiajunxie.service;

import com.jiajunxie.vo.LoanDisbursementCreateVo;
import com.jiajunxie.vo.LoanDisbursementCreatedVo;

public interface LoanDisbursementService {
  LoanDisbursementCreatedVo create(LoanDisbursementCreateVo command);

  LoanDisbursementCreatedVo update(String transactionId, LoanDisbursementCreateVo command);

  boolean hasDisbursement(String loanAccountId);

  /** Reverses an unpaid Loan Engine disbursement after its plan has been removed. */
  int remove(String transactionId);
}
