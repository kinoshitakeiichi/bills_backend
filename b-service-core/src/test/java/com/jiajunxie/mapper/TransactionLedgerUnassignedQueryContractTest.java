package com.jiajunxie.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.domain.transaction.TransactionLedgerQueryCriteria;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TransactionLedgerUnassignedQueryContractTest {

  @Test
  void ledgerCriteriaCarriesDedicatedUnassignedPersonFlag() {
    assertTrue(
        Arrays.stream(TransactionLedgerQueryCriteria.class.getRecordComponents())
            .anyMatch(component ->
                component.getName().equals("unassignedPerson")
                    && component.getType().equals(boolean.class)));
  }
}
