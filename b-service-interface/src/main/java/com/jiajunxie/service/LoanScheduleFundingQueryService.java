package com.jiajunxie.service;

import com.jiajunxie.domain.loan.LoanUnplannedFundingFact;
import java.util.List;

public interface LoanScheduleFundingQueryService {
  List<LoanUnplannedFundingFact> listUnplanned(String loanAccountId);
}
