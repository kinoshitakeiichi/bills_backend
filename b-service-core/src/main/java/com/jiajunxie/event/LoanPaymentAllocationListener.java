package com.jiajunxie.event;

import com.jiajunxie.service.LoanPaymentAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanPaymentAllocationListener {
  private final LoanPaymentAllocationService allocationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLoanPaymentCommitted(LoanPaymentCommittedEvent event) {
    try {
      allocationService.allocate(event.transactionId(), event.scheduleIds());
    } catch (RuntimeException ex) {
      // The accounting fact has already committed. Allocation is derived state and must never
      // retroactively invalidate a real payment. Keep the error observable for explicit retry.
      log.error(
          "Loan payment allocation failed after payment commit. transactionId={}, scheduleIds={}",
          event.transactionId(),
          event.scheduleIds(),
          ex);
    }
  }
}
