package com.jiajunxie.service;

import com.jiajunxie.vo.CreditPurchaseRefundRequestVo;

public interface CreditPurchaseRefundService {
  String refund(String purchaseTransactionId, CreditPurchaseRefundRequestVo request);
  void reverse(String refundTransactionId);
}
