package com.jiajunxie.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.jiajunxie.domain.loan.LoanRecastStrategy;
import com.jiajunxie.domain.loan.LoanRemainderRule;
import com.jiajunxie.domain.loan.LoanRepaymentType;
import com.jiajunxie.domain.loan.LoanScheduleAdjustmentMode;
import com.jiajunxie.domain.loan.LoanScheduleStatus;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanRepaymentScheduleFoundationTest {

  @Test
  void frozenRepaymentTypesAreRepresentedWithoutImplicitDefault() {
    assertArrayEquals(
        new LoanRepaymentType[] {
          LoanRepaymentType.EQUAL_PAYMENT,
          LoanRepaymentType.EQUAL_PRINCIPAL,
          LoanRepaymentType.ZERO_INTEREST,
          LoanRepaymentType.MANUAL
        },
        LoanRepaymentType.values());
  }

  @Test
  void frozenRemainderRecastAndAdjustmentContractsExist() {
    assertEquals(LoanRemainderRule.ROUND_TO_LAST, LoanRemainderRule.valueOf("ROUND_TO_LAST"));
    assertNotNull(LoanRecastStrategy.valueOf("REDUCE_PAYMENT"));
    assertNotNull(LoanRecastStrategy.valueOf("REDUCE_TERM"));
    assertNotNull(LoanScheduleAdjustmentMode.valueOf("ADJUST_LAST"));
    assertNotNull(LoanScheduleAdjustmentMode.valueOf("KEEP_REMAINING"));
  }

  @Test
  void overdueIsDerivedAndNotPersistedAsScheduleLifecycleState() {
    assertThrows(IllegalArgumentException.class, () -> LoanScheduleStatus.valueOf("OVERDUE"));
  }

  @Test
  void loanScheduleDetailUsesDedicatedExtensionTableAndZeroInterestIsLegal() {
    Table table = LoanRepaymentScheduleDetail.class.getAnnotation(Table.class);
    assertEquals("b_loan_repayment_schedule_details", table.name());

    LoanRepaymentScheduleDetail detail = new LoanRepaymentScheduleDetail();
    detail.setScheduledInterestAmount(BigDecimal.ZERO);
    detail.setAnnualRateSnapshot(BigDecimal.ZERO);
    assertEquals(0, detail.getScheduledInterestAmount().signum());
    assertEquals(0, detail.getAnnualRateSnapshot().signum());
  }

  @Test
  void loanPaymentAllocationUsesManyToManyBridgeTable() {
    Table table = LoanPaymentAllocation.class.getAnnotation(Table.class);
    assertEquals("b_loan_payment_allocations", table.name());
  }
}
