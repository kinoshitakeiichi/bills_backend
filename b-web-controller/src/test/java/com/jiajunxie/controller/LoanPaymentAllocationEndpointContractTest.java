package com.jiajunxie.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoanPaymentAllocationEndpointContractTest {
  @Test
  void exposesExistingPaymentAllocationEndpoint() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/jiajunxie/controller/LoanPaymentController.java"));
    assertTrue(source.contains("@PostMapping(\"/{transactionId}/allocate\")"));
    assertTrue(source.contains("allocationService.allocate(transactionId, request.getScheduleIds())"));
  }
}
