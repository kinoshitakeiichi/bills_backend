package com.jiajunxie.domain.transaction;

import java.util.List;

public record TransactionLedgerPage(
    List<TransactionLedgerRow> items,
    int page,
    int pageSize,
    long totalItems,
    int totalPages) {}
