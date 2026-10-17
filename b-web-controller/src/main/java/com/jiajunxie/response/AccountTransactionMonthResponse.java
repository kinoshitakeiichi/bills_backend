package com.jiajunxie.response;

import java.math.BigDecimal;
import java.util.List;

public record AccountTransactionMonthResponse(
    String accountId,
    String month,
    String timezone,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal netChange,
    List<AccountTransactionResponse> transactions) {}
