package com.jiajunxie.response;

import java.math.BigDecimal;

public record TransactionEntryResponse(
    String accountId,
    BigDecimal amount,
    String currency,
    String entryRole) {}
