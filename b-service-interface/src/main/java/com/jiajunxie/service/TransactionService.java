package com.jiajunxie.service;

import com.jiajunxie.vo.AccountTransactionMonthVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionDetailVo;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;

public interface TransactionService {

  String createTransaction(TransactionCreateVo transactionVo);

  TransactionDetailVo getTransaction(String transactionId);

  Integer updateTransaction(String transactionId, TransactionCreateVo transactionVo);

  Integer removeTransaction(String transactionId);

  BigDecimal recalculateBalance(String accountId);

  AccountTransactionMonthVo listAccountTransactions(
      String accountId, YearMonth month, String keyword, ZoneId timezone);
}
