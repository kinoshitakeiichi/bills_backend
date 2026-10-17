package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanRecastExecutionCommand;
import com.jiajunxie.domain.loan.LoanRecastExecutionResult;

public interface LoanRecastExecutionService {
  LoanRecastExecutionResult execute(LoanRecastExecutionCommand command);
}
