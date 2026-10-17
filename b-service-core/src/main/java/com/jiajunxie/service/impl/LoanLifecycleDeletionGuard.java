package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.LoanPaymentDetail;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Central deletion boundary for Loan Engine historical facts.
 *
 * <p>An actual loan payment is a settled historical fact. Once a loan account has any
 * {@link LoanPaymentDetail}, callers must not physically delete the loan account or remove
 * upstream funding/plan facts merely to make the history disappear. Incorrect individual
 * payments must instead be corrected through the dedicated Loan Payment reversal lifecycle.
 */
@Component
@RequiredArgsConstructor
public class LoanLifecycleDeletionGuard {

  private final LoanPaymentDetailMapper loanPaymentDetailMapper;

  public boolean hasPaymentHistory(String loanAccountId) {
    if (loanAccountId == null || loanAccountId.isBlank()) {
      return false;
    }
    Long count =
        loanPaymentDetailMapper.selectCount(
            Wrappers.<LoanPaymentDetail>lambdaQuery()
                .eq(LoanPaymentDetail::getLoanAccountId, loanAccountId));
    return count != null && count > 0;
  }

  /**
   * Protects physical loan-account deletion once actual repayment history exists.
   *
   * <p>This is intentionally stronger and more semantic than the generic "account has entries"
   * guard. Future Loan plan/funding delete lifecycles should reuse the same historical boundary
   * rather than bypassing it.
   */
  public void assertLoanAccountDeletable(String loanAccountId) {
    if (hasPaymentHistory(loanAccountId)) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "贷款已经产生实际还款明细，不能删除贷款；如还款录入错误，请先通过贷款还款撤销流程更正");
    }
  }
}
