package com.jiajunxie.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class LoanPaymentDetailMapperContractTest {
  @Test
  void paymentBreakdownUsesExplicitColumnMappings() throws Exception {
    Method method =
        LoanPaymentDetailMapper.class.getMethod("selectByTransactionIds", List.class);
    Select select = method.getAnnotation(Select.class);
    String sql = String.join("\n", select.value());

    assertTrue(sql.contains("principal_amount AS \"principalAmount\""));
    assertTrue(sql.contains("interest_amount AS \"interestAmount\""));
    assertTrue(sql.contains("fee_amount AS \"feeAmount\""));
    assertTrue(sql.contains("transaction_id AS \"transactionId\""));
  }
}
