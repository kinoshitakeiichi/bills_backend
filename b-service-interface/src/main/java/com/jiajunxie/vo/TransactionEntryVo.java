package com.jiajunxie.vo;

import java.math.BigDecimal;

public record TransactionEntryVo(
    String accountId,
    BigDecimal amount,
    String currency,
    String entryRole) {}
