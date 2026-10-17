package com.jiajunxie.vo;

import java.time.LocalDate;

public record CreditCardStatementUpdateVo(
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate confirmationDate,
    LocalDate scheduledPaymentDate,
    LocalDate calculatedPaymentDate,
    LocalDate manualPaymentDate) {}
