package com.jiajunxie.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanManualInstallmentInput(
    int installmentNo,
    BigDecimal principalAmount,
    BigDecimal interestAmount,
    BigDecimal feeAmount,
    LocalDate paymentDate) {
  public LoanManualInstallmentInput(
      int installmentNo, BigDecimal principalAmount, BigDecimal interestAmount, BigDecimal feeAmount) {
    this(installmentNo, principalAmount, interestAmount, feeAmount, null);
  }
}
