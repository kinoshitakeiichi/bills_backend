package com.jiajunxie.service;

import com.jiajunxie.vo.StatementPaymentAllocationCommandVo;
import com.jiajunxie.vo.StatementPaymentAllocationVo;
import java.math.BigDecimal;
import java.util.List;

public interface StatementPaymentAllocationService {

  List<StatementPaymentAllocationVo> replaceAllocations(
      String paymentTransactionId,
      String targetCreditAccountId,
      BigDecimal paymentAmount,
      String currency,
      List<StatementPaymentAllocationCommandVo> allocations);

  void deleteByPaymentTransactionId(String paymentTransactionId);

  List<StatementPaymentAllocationVo> listByPaymentTransactionId(
      String paymentTransactionId);

  List<StatementPaymentAllocationVo> listByStatementId(String statementId);
}
