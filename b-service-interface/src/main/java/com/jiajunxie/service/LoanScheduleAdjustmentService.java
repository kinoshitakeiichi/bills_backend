package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanScheduleAmountAdjustmentCommand;
import com.jiajunxie.domain.loan.LoanScheduleDateAdjustmentCommand;

public interface LoanScheduleAdjustmentService {
  void adjustAmount(LoanScheduleAmountAdjustmentCommand command);
  void adjustDate(LoanScheduleDateAdjustmentCommand command);
}
