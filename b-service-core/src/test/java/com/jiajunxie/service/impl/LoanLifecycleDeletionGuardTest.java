package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.mapper.LoanPaymentDetailMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanLifecycleDeletionGuardTest {

  @Mock LoanPaymentDetailMapper loanPaymentDetailMapper;

  @Test
  void loanWithoutPaymentHistoryMayPassDeletionBoundary() {
    when(loanPaymentDetailMapper.selectCount(any())).thenReturn(0L);

    assertDoesNotThrow(
        () -> new LoanLifecycleDeletionGuard(loanPaymentDetailMapper)
            .assertLoanAccountDeletable("loan"));
  }

  @Test
  void loanWithActualPaymentHistoryCannotBeDeleted() {
    when(loanPaymentDetailMapper.selectCount(any())).thenReturn(1L);

    assertThrows(
        BusinessException.class,
        () -> new LoanLifecycleDeletionGuard(loanPaymentDetailMapper)
            .assertLoanAccountDeletable("loan"));
  }
}
