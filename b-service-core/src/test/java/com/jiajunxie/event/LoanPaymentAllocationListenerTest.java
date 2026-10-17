package com.jiajunxie.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import com.jiajunxie.service.LoanPaymentAllocationService;
import org.junit.jupiter.api.Test;

class LoanPaymentAllocationListenerTest {
  @Test
  void allocationFailureNeverReversesCommittedPaymentFact() {
    LoanPaymentAllocationService allocationService = mock(LoanPaymentAllocationService.class);
    when(allocationService.allocate("pay", java.util.List.of("schedule-1", "schedule-2"))).thenThrow(new IllegalStateException("schedule broken"));
    var listener = new LoanPaymentAllocationListener(allocationService);

    assertDoesNotThrow(() -> listener.onLoanPaymentCommitted(new LoanPaymentCommittedEvent("pay", java.util.List.of("schedule-1", "schedule-2"))));
    verify(allocationService).allocate("pay", java.util.List.of("schedule-1", "schedule-2"));
  }
}
