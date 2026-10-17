package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jiajunxie.domain.loan.LoanRecastPreviewCommand;
import com.jiajunxie.domain.loan.LoanRecastStrategy;
import com.jiajunxie.domain.loan.LoanScheduleItem;
import com.jiajunxie.domain.loan.LoanSchedulePlanView;
import com.jiajunxie.service.LoanScheduleQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanRecastPreviewServiceImplTest {

  @Test
  void reducePaymentKeepsRemainingTermAndLowersNextPaymentAfterExtraPrincipal() {
    LoanScheduleQueryService query = mock(LoanScheduleQueryService.class);
    when(query.getByPlanId(eq("plan"), any(LocalDate.class))).thenReturn(plan());
    var service = new LoanRecastPreviewServiceImpl(query, new LoanRepaymentCalculatorImpl());

    var result =
        service.preview(
            new LoanRecastPreviewCommand(
                "plan", 2, new BigDecimal("50000"), LoanRecastStrategy.REDUCE_PAYMENT));

    assertEquals(2, result.currentRemainingInstallments());
    assertEquals(2, result.proposedRemainingInstallments());
    assertTrue(result.proposedNextPaymentAmount().compareTo(result.currentNextPaymentAmount()) < 0);
    assertFalse(result.persistenceApplied());
  }

  @Test
  void reduceTermFindsShorterTermWithoutIncreasingTheCurrentNextPayment() {
    LoanScheduleQueryService query = mock(LoanScheduleQueryService.class);
    when(query.getByPlanId(eq("plan"), any(LocalDate.class))).thenReturn(plan());
    var service = new LoanRecastPreviewServiceImpl(query, new LoanRepaymentCalculatorImpl());

    var result =
        service.preview(
            new LoanRecastPreviewCommand(
                "plan", 2, new BigDecimal("30000"), LoanRecastStrategy.REDUCE_TERM));

    assertTrue(result.proposedRemainingInstallments() < result.currentRemainingInstallments());
    assertTrue(result.proposedNextPaymentAmount().compareTo(result.currentNextPaymentAmount()) <= 0);
    assertFalse(result.persistenceApplied());
  }

  private LoanSchedulePlanView plan() {
    return new LoanSchedulePlanView(
        "plan",
        "funding",
        "loan",
        "JPY",
        new BigDecimal("120000"),
        new BigDecimal("120000"),
        new BigDecimal("2400"),
        BigDecimal.ZERO,
        List.of(
            item(1, "40000", "1200", "80000"),
            item(2, "40000", "800", "40000"),
            item(3, "40000", "400", "0")));
  }

  private LoanScheduleItem item(
      int no, String principal, String interest, String remaining) {
    BigDecimal p = new BigDecimal(principal);
    BigDecimal i = new BigDecimal(interest);
    return new LoanScheduleItem(
        "s" + no,
        "plan",
        "funding",
        "loan",
        no,
        3,
        "EQUAL_PRINCIPAL",
        p,
        i,
        BigDecimal.ZERO,
        p,
        i,
        BigDecimal.ZERO,
        p.add(i),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        p,
        i,
        BigDecimal.ZERO,
        p.add(i),
        new BigDecimal(remaining),
        new BigDecimal("0.12"),
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 8 + no, 10),
        LocalDate.of(2026, 8 + no, 10),
        null,
        LocalDate.of(2026, 8 + no, 10),
        "PENDING",
        "PENDING",
        "ADJUST_LAST",
        "LOAN_SCHEDULE_V1",
        0,
        1);
  }
}
