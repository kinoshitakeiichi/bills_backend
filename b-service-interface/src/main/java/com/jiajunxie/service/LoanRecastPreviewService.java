package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanRecastPreviewCommand;
import com.jiajunxie.domain.loan.LoanRecastPreviewResult;

public interface LoanRecastPreviewService {
  LoanRecastPreviewResult preview(LoanRecastPreviewCommand command);
}
