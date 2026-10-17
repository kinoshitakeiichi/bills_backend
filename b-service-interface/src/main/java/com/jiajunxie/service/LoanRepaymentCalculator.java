package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanRepaymentCalculationRequest;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationResult;

public interface LoanRepaymentCalculator {
  LoanRepaymentCalculationResult calculate(LoanRepaymentCalculationRequest request);
}
