package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanSchedulePlanView;
import java.time.LocalDate;
import java.util.List;

public interface LoanScheduleQueryService {
  List<LoanSchedulePlanView> listByLoanAccount(String loanAccountId, LocalDate asOfDate);
  LoanSchedulePlanView getByPlanId(String installmentPlanId, LocalDate asOfDate);
}
