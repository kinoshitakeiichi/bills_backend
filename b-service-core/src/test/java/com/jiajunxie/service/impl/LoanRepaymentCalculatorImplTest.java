package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.jiajunxie.domain.loan.LoanManualInstallmentInput;
import com.jiajunxie.domain.loan.LoanRateSnapshotInput;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationRequest;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationResult;
import com.jiajunxie.domain.loan.LoanRepaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanRepaymentCalculatorImplTest {
  private LoanRepaymentCalculatorImpl calculator;

  @BeforeEach
  void setUp() {
    calculator = new LoanRepaymentCalculatorImpl();
  }

  @Test
  void zeroInterestJpyPushesRemainderToLastInstallment() {
    LoanRepaymentCalculationResult result =
        calculator.calculate(
            request(
                "100000",
                LoanRepaymentType.ZERO_INTEREST,
                3,
                0,
                "0",
                List.of(),
                List.of()));

    assertEquals(new BigDecimal("33333"), result.installments().get(0).principalAmount());
    assertEquals(new BigDecimal("33333"), result.installments().get(1).principalAmount());
    assertEquals(new BigDecimal("33334"), result.installments().get(2).principalAmount());
    assertEquals(0, result.totalInterest().signum());
    assertEquals(new BigDecimal("100000"), result.totalPrincipal());
    assertEquals(0, result.installments().get(2).remainingPrincipalAfter().signum());
  }

  @Test
  void zeroInterestTwoDecimalCurrencyAlsoReconcilesExactly() {
    LoanRepaymentCalculationResult result =
        calculator.calculate(
            request(
                "100.00",
                LoanRepaymentType.ZERO_INTEREST,
                3,
                2,
                "0",
                List.of(),
                List.of()));

    assertEquals(new BigDecimal("33.33"), result.installments().get(0).principalAmount());
    assertEquals(new BigDecimal("33.33"), result.installments().get(1).principalAmount());
    assertEquals(new BigDecimal("33.34"), result.installments().get(2).principalAmount());
    assertEquals(new BigDecimal("100.00"), result.totalPrincipal());
  }

  @Test
  void equalPrincipalUsesOpeningBalanceForInterestAndLastPrincipalAbsorbsRemainder() {
    LoanRepaymentCalculationResult result =
        calculator.calculate(
            request(
                "120000",
                LoanRepaymentType.EQUAL_PRINCIPAL,
                12,
                0,
                "0.12",
                List.of(),
                List.of()));

    assertEquals(new BigDecimal("10000"), result.installments().get(0).principalAmount());
    assertEquals(new BigDecimal("1200"), result.installments().get(0).interestAmount());
    assertEquals(new BigDecimal("1100"), result.installments().get(1).interestAmount());
    assertEquals(new BigDecimal("110000"), result.installments().get(0).remainingPrincipalAfter());
    assertEquals(new BigDecimal("120000"), result.totalPrincipal());
    assertEquals(0, result.installments().get(11).remainingPrincipalAfter().signum());
  }

  @Test
  void equalPaymentUsesAnnuityAndCorrectsOnlyFinalPrincipalRemainder() {
    LoanRepaymentCalculationResult result =
        calculator.calculate(
            request(
                "120000",
                LoanRepaymentType.EQUAL_PAYMENT,
                12,
                0,
                "0.12",
                List.of(),
                List.of()));

    assertEquals(new BigDecimal("9462"), result.installments().get(0).principalAmount());
    assertEquals(new BigDecimal("1200"), result.installments().get(0).interestAmount());
    assertEquals(new BigDecimal("10662"), result.installments().get(0).totalAmount());
    assertEquals(new BigDecimal("120000"), result.totalPrincipal());
    assertEquals(new BigDecimal("7942"), result.totalInterest());
    assertEquals(new BigDecimal("10660"), result.installments().get(11).totalAmount());
    assertEquals(0, result.installments().get(11).remainingPrincipalAfter().signum());
  }

  @Test
  void equalPaymentAtZeroRateWorksWithoutDivideByZero() {
    LoanRepaymentCalculationResult result =
        calculator.calculate(
            request(
                "100000",
                LoanRepaymentType.EQUAL_PAYMENT,
                3,
                0,
                "0",
                List.of(),
                List.of()));

    assertEquals(0, result.totalInterest().signum());
    assertEquals(new BigDecimal("100000"), result.totalPrincipal());
    assertEquals(0, result.installments().get(2).remainingPrincipalAfter().signum());
  }

  @Test
  void variableRateSnapshotRecastsEqualPaymentFromEffectiveInstallmentOnly() {
    LocalDate effectiveFrom = LocalDate.of(2027, 1, 1);
    LoanRepaymentCalculationResult fixed =
        calculator.calculate(
            request(
                "120000",
                LoanRepaymentType.EQUAL_PAYMENT,
                12,
                0,
                "0.12",
                List.of(),
                List.of()));
    LoanRepaymentCalculationResult variable =
        calculator.calculate(
            request(
                "120000",
                LoanRepaymentType.EQUAL_PAYMENT,
                12,
                0,
                "0.12",
                List.of(new LoanRateSnapshotInput(7, new BigDecimal("0.24"), effectiveFrom)),
                List.of()));

    for (int i = 0; i < 6; i++) {
      assertEquals(fixed.installments().get(i), variable.installments().get(i));
    }
    assertEquals(new BigDecimal("0.24"), variable.installments().get(6).annualRateSnapshot());
    assertEquals(effectiveFrom, variable.installments().get(6).rateEffectiveFrom());
    assertTrue(
        variable.installments().get(6).totalAmount()
            .compareTo(fixed.installments().get(6).totalAmount()) > 0);
    assertEquals(new BigDecimal("120000"), variable.totalPrincipal());
    assertEquals(0, variable.installments().get(11).remainingPrincipalAfter().signum());
  }

  @Test
  void rateSnapshotAtFirstInstallmentCanOverrideDefaultRate() {
    LocalDate effectiveFrom = LocalDate.of(2026, 8, 1);
    LoanRepaymentCalculationResult result =
        calculator.calculate(
            request(
                "120000",
                LoanRepaymentType.EQUAL_PRINCIPAL,
                12,
                0,
                "0.01",
                List.of(new LoanRateSnapshotInput(1, new BigDecimal("0.12"), effectiveFrom)),
                List.of()));

    assertEquals(new BigDecimal("1200"), result.installments().get(0).interestAmount());
    assertEquals(effectiveFrom, result.installments().get(0).rateEffectiveFrom());
  }

  @Test
  void manualSchedulePreservesUserPrincipalInterestAndFeeExactly() {
    LoanRepaymentCalculationResult result =
        calculator.calculate(
            request(
                "100000",
                LoanRepaymentType.MANUAL,
                2,
                0,
                "0",
                List.of(),
                List.of(
                    new LoanManualInstallmentInput(
                        1, new BigDecimal("30000"), new BigDecimal("500"), new BigDecimal("100")),
                    new LoanManualInstallmentInput(
                        2, new BigDecimal("70000"), new BigDecimal("200"), BigDecimal.ZERO))));

    assertEquals(new BigDecimal("100000"), result.totalPrincipal());
    assertEquals(new BigDecimal("700"), result.totalInterest());
    assertEquals(new BigDecimal("100"), result.totalFee());
    assertEquals(new BigDecimal("100800"), result.totalPayment());
    assertEquals(new BigDecimal("70000"), result.installments().get(0).remainingPrincipalAfter());
    assertEquals(0, result.installments().get(1).remainingPrincipalAfter().signum());
  }

  @Test
  void manualScheduleRejectsPrincipalThatDoesNotReconcile() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            calculator.calculate(
                request(
                    "100000",
                    LoanRepaymentType.MANUAL,
                    2,
                    0,
                    "0",
                    List.of(),
                    List.of(
                        new LoanManualInstallmentInput(
                            1, new BigDecimal("30000"), BigDecimal.ZERO, BigDecimal.ZERO),
                        new LoanManualInstallmentInput(
                            2, new BigDecimal("60000"), BigDecimal.ZERO, BigDecimal.ZERO)))));
  }

  @Test
  void zeroInterestModeRejectsNonZeroRate() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            calculator.calculate(
                request(
                    "100000",
                    LoanRepaymentType.ZERO_INTEREST,
                    3,
                    0,
                    "0.01",
                    List.of(),
                    List.of())));
  }

  @Test
  void duplicateRateSnapshotInstallmentIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            calculator.calculate(
                request(
                    "100000",
                    LoanRepaymentType.EQUAL_PRINCIPAL,
                    3,
                    0,
                    "0.01",
                    List.of(
                        new LoanRateSnapshotInput(2, new BigDecimal("0.02"), null),
                        new LoanRateSnapshotInput(2, new BigDecimal("0.03"), null)),
                    List.of())));
  }

  @Test
  void inputMoneyScaleIsValidatedInsteadOfSilentlyRounded() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            calculator.calculate(
                request(
                    "100.001",
                    LoanRepaymentType.ZERO_INTEREST,
                    2,
                    2,
                    "0",
                    List.of(),
                    List.of())));
  }

  private LoanRepaymentCalculationRequest request(
      String principal,
      LoanRepaymentType type,
      int installments,
      int amountScale,
      String annualRate,
      List<LoanRateSnapshotInput> rates,
      List<LoanManualInstallmentInput> manual) {
    return new LoanRepaymentCalculationRequest(
        new BigDecimal(principal),
        type,
        installments,
        amountScale,
        12,
        new BigDecimal(annualRate),
        rates,
        manual);
  }
}
