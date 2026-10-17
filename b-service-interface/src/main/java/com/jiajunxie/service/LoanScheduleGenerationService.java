package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanScheduleGenerationCommand;
import com.jiajunxie.domain.loan.LoanScheduleGenerationResult;
import com.jiajunxie.domain.loan.LoanScheduleGenerationPreviewResult;

public interface LoanScheduleGenerationService {
  LoanScheduleGenerationPreviewResult preview(LoanScheduleGenerationCommand command);
  LoanScheduleGenerationResult generate(LoanScheduleGenerationCommand command);

  /** Removes an unpaid Loan repayment plan and returns it to unplanned funding. */
  int removePlan(String planId);
}
