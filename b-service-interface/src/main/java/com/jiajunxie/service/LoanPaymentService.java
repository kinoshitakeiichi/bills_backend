package com.jiajunxie.service;

import com.jiajunxie.vo.LoanPaymentCreateVo;
import com.jiajunxie.vo.LoanPaymentCreatedVo;
import com.jiajunxie.vo.LoanPaymentUpdateVo;

public interface LoanPaymentService {
  LoanPaymentCreatedVo create(LoanPaymentCreateVo command);

  LoanPaymentCreatedVo update(String transactionId, LoanPaymentUpdateVo command);

  /** Reverses one erroneous Loan Engine payment without deleting the loan or funding fact. */
  int reverse(String transactionId);
}
