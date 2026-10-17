package com.jiajunxie.vo;

import java.math.BigDecimal;
import java.util.List;

public record AccountTransactionMonthVo(
    String accountId,
    String month,
    String timezone,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal netChange,
    List<AccountTransactionVo> transactions) {}
