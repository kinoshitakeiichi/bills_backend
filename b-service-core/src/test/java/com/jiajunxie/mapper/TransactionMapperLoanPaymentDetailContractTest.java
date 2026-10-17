package com.jiajunxie.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Instant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class TransactionMapperLoanPaymentDetailContractTest {
  @Test
  void accountHistoryProjectsLoanPaymentBreakdown() throws Exception {
    Method method = TransactionMapper.class.getMethod(
        "selectAccountTransactions", String.class, Instant.class, Instant.class, String.class);
    Select select = method.getAnnotation(Select.class);
    String sql = String.join("\n", select.value());

    assertTrue(sql.contains("LEFT JOIN bills.b_loan_payment_details loan_payment"));
    assertTrue(sql.contains("loan_payment.principal_amount AS \"loanPrincipalAmount\""));
    assertTrue(sql.contains("loan_payment.interest_amount AS \"loanInterestAmount\""));
    assertTrue(sql.contains("loan_payment.fee_amount AS \"loanFeeAmount\""));
    assertTrue(sql.contains("\"loanTotalPaymentAmount\""));
  }
}
