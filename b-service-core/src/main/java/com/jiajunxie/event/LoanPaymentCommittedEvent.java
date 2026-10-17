package com.jiajunxie.event;

import java.util.List;

public record LoanPaymentCommittedEvent(String transactionId, List<String> scheduleIds) {}
