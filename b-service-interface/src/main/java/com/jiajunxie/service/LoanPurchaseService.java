package com.jiajunxie.service;

import com.jiajunxie.vo.LoanPurchaseCreateVo;
import com.jiajunxie.vo.LoanPurchaseCreatedVo;
import com.jiajunxie.vo.LoanPurchaseAdoptedVo;

public interface LoanPurchaseService {
  LoanPurchaseCreatedVo create(LoanPurchaseCreateVo command);

  LoanPurchaseCreatedVo update(String transactionId, LoanPurchaseCreateVo command);

  LoanPurchaseAdoptedVo adoptLegacyPurchase(String transactionId, String loanAccountId);
  boolean hasPurchase(String loanAccountId);

  /** Reverses an unpaid Loan Engine financing purchase after its plan has been removed. */
  int remove(String transactionId);
}
