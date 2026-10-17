package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.entity.Transaction;
import org.junit.jupiter.api.Test;

class TransactionMutationPolicyTest {
  private final TransactionMutationPolicy policy = new TransactionMutationPolicy();
  private final TransactionReadonlyResolver resolver = new TransactionReadonlyResolver(policy);

  @Test
  void ledgerReadonlyAndGenericMutationShareOneLifecycleRule() {
    for (String type :
        new String[] {
          "EXCHANGE", "CREDIT_REFUND", "LOAN_DISBURSEMENT", "LOAN_PURCHASE", "LOAN_PAYMENT"
        }) {
      Transaction transaction = transaction(type, null);
      assertTrue(policy.isLifecycleReadonly(transaction));
      assertTrue(resolver.isReadonly(transaction));
      assertEquals(policy.lifecycleReadonlyReason(transaction), resolver.reason(transaction));
    }

    for (String origin : new String[] {"STATEMENT_ADJUSTMENT", "STATEMENT_ADJUSTMENT_REVERSAL"}) {
      Transaction transaction = transaction("EXPENSE", origin);
      assertTrue(policy.isLifecycleReadonly(transaction));
      assertTrue(resolver.isReadonly(transaction));
      assertEquals(policy.lifecycleReadonlyReason(transaction), resolver.reason(transaction));
    }
  }

  @Test
  void ordinaryCreditPaymentAndCreditPurchaseRemainOutsideLifecycleReadonlySet() {
    for (String type :
        new String[] {"EXPENSE", "INCOME", "REFUND", "CREDIT_PAYMENT", "CREDIT_PURCHASE"}) {
      Transaction transaction = transaction(type, null);
      assertFalse(policy.isLifecycleReadonly(transaction));
      assertFalse(resolver.isReadonly(transaction));
      assertNull(policy.lifecycleReadonlyReason(transaction));
    }
  }

  private Transaction transaction(String type, String originCode) {
    Transaction transaction = new Transaction();
    transaction.setTransactionType(type);
    transaction.setOriginCode(originCode);
    return transaction;
  }
}
