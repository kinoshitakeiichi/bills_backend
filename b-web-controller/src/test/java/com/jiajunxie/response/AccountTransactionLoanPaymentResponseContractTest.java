package com.jiajunxie.response;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AccountTransactionLoanPaymentResponseContractTest {

  @Test
  void finalAccountTransactionHttpResponseExposesLoanPaymentBreakdown() {
    Set<String> names =
        Arrays.stream(AccountTransactionResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

    assertNotNull(names.contains("originCode") ? Boolean.TRUE : null);
    assertNotNull(names.contains("originReferenceId") ? Boolean.TRUE : null);
    assertNotNull(names.contains("loanPrincipalAmount") ? Boolean.TRUE : null);
    assertNotNull(names.contains("loanInterestAmount") ? Boolean.TRUE : null);
    assertNotNull(names.contains("loanFeeAmount") ? Boolean.TRUE : null);
    assertNotNull(names.contains("loanTotalPaymentAmount") ? Boolean.TRUE : null);
  }
}
