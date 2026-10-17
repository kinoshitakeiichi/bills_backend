package com.jiajunxie.service.impl;

import com.jiajunxie.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ledger-facing projection of the shared transaction mutation policy. */
@Component
@RequiredArgsConstructor
public class TransactionReadonlyResolver {
  private final TransactionMutationPolicy mutationPolicy;

  public boolean isReadonly(Transaction transaction) {
    return mutationPolicy.isLifecycleReadonly(transaction);
  }

  public String reason(Transaction transaction) {
    return mutationPolicy.lifecycleReadonlyReason(transaction);
  }
}
