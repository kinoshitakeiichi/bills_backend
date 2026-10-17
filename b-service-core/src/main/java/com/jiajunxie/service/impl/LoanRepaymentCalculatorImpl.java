package com.jiajunxie.service.impl;

import com.jiajunxie.domain.loan.LoanManualInstallmentInput;
import com.jiajunxie.domain.loan.LoanRateSnapshotInput;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationRequest;
import com.jiajunxie.domain.loan.LoanRepaymentCalculationResult;
import com.jiajunxie.domain.loan.LoanRepaymentInstallment;
import com.jiajunxie.domain.loan.LoanRepaymentType;
import com.jiajunxie.service.LoanRepaymentCalculator;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LoanRepaymentCalculatorImpl implements LoanRepaymentCalculator {
  public static final String CALCULATION_VERSION = "LOAN_SCHEDULE_V1";
  private static final MathContext MC = new MathContext(24, RoundingMode.HALF_UP);
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  @Override
  public LoanRepaymentCalculationResult calculate(LoanRepaymentCalculationRequest request) {
    validateRequest(request);
    RateTimeline rates = buildRateTimeline(request);

    List<LoanRepaymentInstallment> installments =
        switch (request.repaymentType()) {
          case ZERO_INTEREST -> calculateZeroInterest(request, rates);
          case EQUAL_PRINCIPAL -> calculateEqualPrincipal(request, rates);
          case EQUAL_PAYMENT -> calculateEqualPayment(request, rates);
          case MANUAL -> calculateManual(request, rates);
        };

    BigDecimal totalPrincipal = sum(installments, AmountField.PRINCIPAL);
    BigDecimal totalInterest = sum(installments, AmountField.INTEREST);
    BigDecimal totalFee = sum(installments, AmountField.FEE);
    BigDecimal totalPayment = totalPrincipal.add(totalInterest).add(totalFee);

    if (totalPrincipal.compareTo(request.principal()) != 0) {
      throw new IllegalStateException(
          "Calculated principal does not reconcile to original principal: "
              + totalPrincipal
              + " != "
              + request.principal());
    }
    if (!installments.isEmpty()
        && installments.get(installments.size() - 1).remainingPrincipalAfter().signum() != 0) {
      throw new IllegalStateException("Final remaining principal must be zero");
    }

    return new LoanRepaymentCalculationResult(
        request.principal(),
        totalPrincipal,
        totalInterest,
        totalFee,
        totalPayment,
        CALCULATION_VERSION,
        List.copyOf(installments));
  }

  private List<LoanRepaymentInstallment> calculateZeroInterest(
      LoanRepaymentCalculationRequest request, RateTimeline rates) {
    ensureZeroInterestRates(request, rates);
    BigDecimal remaining = request.principal();
    BigDecimal regularPrincipal =
        request.principal().divide(
            BigDecimal.valueOf(request.installmentCount()),
            request.amountScale(),
            RoundingMode.DOWN);

    List<LoanRepaymentInstallment> result = new ArrayList<>();
    for (int installmentNo = 1; installmentNo <= request.installmentCount(); installmentNo++) {
      BigDecimal principal =
          installmentNo == request.installmentCount() ? remaining : regularPrincipal;
      remaining = normalizeZero(remaining.subtract(principal));
      RatePoint rate = rates.forInstallment(installmentNo);
      result.add(
          installment(
              installmentNo, principal, ZERO, ZERO, remaining, ZERO, rate.effectiveFrom()));
    }
    return result;
  }

  private List<LoanRepaymentInstallment> calculateEqualPrincipal(
      LoanRepaymentCalculationRequest request, RateTimeline rates) {
    BigDecimal remaining = request.principal();
    BigDecimal regularPrincipal =
        request.principal().divide(
            BigDecimal.valueOf(request.installmentCount()),
            request.amountScale(),
            RoundingMode.DOWN);

    List<LoanRepaymentInstallment> result = new ArrayList<>();
    for (int installmentNo = 1; installmentNo <= request.installmentCount(); installmentNo++) {
      RatePoint rate = rates.forInstallment(installmentNo);
      BigDecimal interest =
          roundMoney(
              remaining.multiply(periodicRate(rate.annualRate(), request.periodsPerYear()), MC),
              request.amountScale());
      BigDecimal principal =
          installmentNo == request.installmentCount() ? remaining : regularPrincipal;
      remaining = normalizeZero(remaining.subtract(principal));
      result.add(
          installment(
              installmentNo,
              principal,
              interest,
              ZERO,
              remaining,
              rate.annualRate(),
              rate.effectiveFrom()));
    }
    return result;
  }

  private List<LoanRepaymentInstallment> calculateEqualPayment(
      LoanRepaymentCalculationRequest request, RateTimeline rates) {
    BigDecimal remaining = request.principal();
    BigDecimal paymentAmount = null;
    BigDecimal previousRate = null;

    List<LoanRepaymentInstallment> result = new ArrayList<>();
    for (int installmentNo = 1; installmentNo <= request.installmentCount(); installmentNo++) {
      RatePoint rate = rates.forInstallment(installmentNo);
      int remainingPeriods = request.installmentCount() - installmentNo + 1;

      if (paymentAmount == null || previousRate == null || previousRate.compareTo(rate.annualRate()) != 0) {
        paymentAmount =
            roundMoney(
                annuityPayment(
                    remaining,
                    rate.annualRate(),
                    request.periodsPerYear(),
                    remainingPeriods),
                request.amountScale());
        previousRate = rate.annualRate();
      }

      BigDecimal interest =
          roundMoney(
              remaining.multiply(periodicRate(rate.annualRate(), request.periodsPerYear()), MC),
              request.amountScale());

      BigDecimal principal;
      if (installmentNo == request.installmentCount()) {
        principal = remaining;
      } else {
        principal = paymentAmount.subtract(interest);
        if (principal.signum() <= 0) {
          throw new IllegalArgumentException(
              "Rounded equal-payment amount does not cover installment interest");
        }
        if (principal.compareTo(remaining) > 0) {
          principal = remaining;
        }
      }

      remaining = normalizeZero(remaining.subtract(principal));
      result.add(
          installment(
              installmentNo,
              principal,
              interest,
              ZERO,
              remaining,
              rate.annualRate(),
              rate.effectiveFrom()));
    }
    return result;
  }

  private List<LoanRepaymentInstallment> calculateManual(
      LoanRepaymentCalculationRequest request, RateTimeline rates) {
    List<LoanManualInstallmentInput> manual = request.manualInstallments();
    if (manual == null || manual.size() != request.installmentCount()) {
      throw new IllegalArgumentException(
          "MANUAL repayment requires exactly installmentCount manual rows");
    }

    List<LoanManualInstallmentInput> rows =
        manual.stream().sorted(Comparator.comparingInt(LoanManualInstallmentInput::installmentNo)).toList();

    BigDecimal remaining = request.principal();
    List<LoanRepaymentInstallment> result = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      LoanManualInstallmentInput row = rows.get(i);
      int expectedNo = i + 1;
      if (row.installmentNo() != expectedNo) {
        throw new IllegalArgumentException("MANUAL installment numbers must be consecutive from 1");
      }
      requireNonNegative(row.principalAmount(), "manual principalAmount");
      requireNonNegative(row.interestAmount(), "manual interestAmount");
      requireNonNegative(row.feeAmount(), "manual feeAmount");
      ensureScale(row.principalAmount(), request.amountScale(), "manual principalAmount");
      ensureScale(row.interestAmount(), request.amountScale(), "manual interestAmount");
      ensureScale(row.feeAmount(), request.amountScale(), "manual feeAmount");

      if (row.principalAmount().compareTo(remaining) > 0) {
        throw new IllegalArgumentException("MANUAL principal exceeds remaining principal");
      }
      remaining = normalizeZero(remaining.subtract(row.principalAmount()));
      RatePoint rate = rates.forInstallment(row.installmentNo());
      result.add(
          installment(
              row.installmentNo(),
              row.principalAmount(),
              row.interestAmount(),
              row.feeAmount(),
              remaining,
              rate.annualRate(),
              rate.effectiveFrom()));
    }

    if (remaining.signum() != 0) {
      throw new IllegalArgumentException(
          "MANUAL principal rows must reconcile exactly to original principal");
    }
    return result;
  }

  private LoanRepaymentInstallment installment(
      int no,
      BigDecimal principal,
      BigDecimal interest,
      BigDecimal fee,
      BigDecimal remaining,
      BigDecimal annualRate,
      LocalDate rateEffectiveFrom) {
    BigDecimal total = principal.add(interest).add(fee);
    return new LoanRepaymentInstallment(
        no, principal, interest, fee, total, remaining, annualRate, rateEffectiveFrom);
  }

  private BigDecimal annuityPayment(
      BigDecimal principal, BigDecimal annualRate, int periodsPerYear, int periods) {
    if (annualRate.signum() == 0) {
      return principal.divide(BigDecimal.valueOf(periods), MC);
    }
    BigDecimal rate = periodicRate(annualRate, periodsPerYear);
    BigDecimal factor = BigDecimal.ONE.add(rate, MC).pow(periods, MC);
    return principal
        .multiply(rate, MC)
        .multiply(factor, MC)
        .divide(factor.subtract(BigDecimal.ONE, MC), MC);
  }

  private BigDecimal periodicRate(BigDecimal annualRate, int periodsPerYear) {
    return annualRate.divide(BigDecimal.valueOf(periodsPerYear), MC);
  }

  private RateTimeline buildRateTimeline(LoanRepaymentCalculationRequest request) {
    Map<Integer, RatePoint> changes = new HashMap<>();
    changes.put(1, new RatePoint(request.defaultAnnualRate(), null));

    if (request.rateSnapshots() != null) {
      Set<Integer> seenSnapshotInstallments = new HashSet<>();
      for (LoanRateSnapshotInput input : request.rateSnapshots()) {
        if (input == null) throw new IllegalArgumentException("rateSnapshots cannot contain null");
        if (input.effectiveInstallmentNo() < 1
            || input.effectiveInstallmentNo() > request.installmentCount()) {
          throw new IllegalArgumentException("rate snapshot installment is outside schedule");
        }
        if (!seenSnapshotInstallments.add(input.effectiveInstallmentNo())) {
          throw new IllegalArgumentException("duplicate rate snapshot installment");
        }
        requireNonNegative(input.annualRate(), "snapshot annualRate");
        changes.put(
            input.effectiveInstallmentNo(),
            new RatePoint(input.annualRate(), input.effectiveFrom()));
      }
    }
    return new RateTimeline(changes, request.installmentCount());
  }

  private void validateRequest(LoanRepaymentCalculationRequest request) {
    if (request == null) throw new IllegalArgumentException("request is required");
    if (request.principal() == null || request.principal().signum() <= 0) {
      throw new IllegalArgumentException("principal must be greater than zero");
    }
    if (request.repaymentType() == null) {
      throw new IllegalArgumentException("repaymentType is required");
    }
    if (request.installmentCount() < 1) {
      throw new IllegalArgumentException("installmentCount must be at least 1");
    }
    if (request.amountScale() < 0 || request.amountScale() > 4) {
      throw new IllegalArgumentException("amountScale must be between 0 and 4");
    }
    ensureScale(request.principal(), request.amountScale(), "principal");
    if (request.periodsPerYear() < 1) {
      throw new IllegalArgumentException("periodsPerYear must be at least 1");
    }
    requireNonNegative(request.defaultAnnualRate(), "defaultAnnualRate");
  }

  private void ensureZeroInterestRates(
      LoanRepaymentCalculationRequest request, RateTimeline rates) {
    for (int i = 1; i <= request.installmentCount(); i++) {
      if (rates.forInstallment(i).annualRate().signum() != 0) {
        throw new IllegalArgumentException("ZERO_INTEREST repayment requires zero annual rate");
      }
    }
  }

  private void requireNonNegative(BigDecimal value, String field) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException(field + " must be non-negative");
    }
  }

  private void ensureScale(BigDecimal value, int amountScale, String field) {
    if (value.stripTrailingZeros().scale() > amountScale) {
      throw new IllegalArgumentException(field + " exceeds configured amountScale");
    }
  }

  private BigDecimal roundMoney(BigDecimal amount, int scale) {
    return amount.setScale(scale, RoundingMode.HALF_UP);
  }

  private BigDecimal normalizeZero(BigDecimal value) {
    return value.signum() == 0 ? ZERO.setScale(Math.max(0, value.scale())) : value;
  }

  private BigDecimal sum(List<LoanRepaymentInstallment> installments, AmountField field) {
    return installments.stream()
        .map(
            installment ->
                switch (field) {
                  case PRINCIPAL -> installment.principalAmount();
                  case INTEREST -> installment.interestAmount();
                  case FEE -> installment.feeAmount();
                })
        .reduce(ZERO, BigDecimal::add);
  }

  private enum AmountField {
    PRINCIPAL,
    INTEREST,
    FEE
  }

  private record RatePoint(BigDecimal annualRate, LocalDate effectiveFrom) {}

  private static final class RateTimeline {
    private final Map<Integer, RatePoint> byInstallment;

    private RateTimeline(Map<Integer, RatePoint> changes, int installmentCount) {
      this.byInstallment = new HashMap<>();
      RatePoint current = null;
      for (int i = 1; i <= installmentCount; i++) {
        if (changes.containsKey(i)) current = changes.get(i);
        if (current == null) throw new IllegalArgumentException("rate timeline has no initial rate");
        byInstallment.put(i, current);
      }
    }

    private RatePoint forInstallment(int installmentNo) {
      return byInstallment.get(installmentNo);
    }
  }
}
