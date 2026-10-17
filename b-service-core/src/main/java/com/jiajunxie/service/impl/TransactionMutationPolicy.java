package com.jiajunxie.service.impl;

import com.jiajunxie.entity.Transaction;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TransactionMutationPolicy {
  private static final Set<String> LOAN_TYPES =
      Set.of("LOAN_DISBURSEMENT", "LOAN_PURCHASE", "LOAN_PAYMENT");
  private static final Set<String> SYSTEM_STATEMENT_ORIGINS =
      Set.of("STATEMENT_ADJUSTMENT", "STATEMENT_ADJUSTMENT_REVERSAL");

  public boolean isLifecycleReadonly(Transaction transaction) {
    return lifecycleReadonlyReason(transaction) != null;
  }

  public String lifecycleReadonlyReason(Transaction transaction) {
    if (transaction == null) return null;

    String type = normalize(transaction.getTransactionType());
    if ("EXCHANGE".equals(type)) {
      return "FX 交易只能通过 FX 生命周期处理，不能普通修改或删除";
    }
    if ("CREDIT_REFUND".equals(type)) {
      return "信用卡退款只能通过 Credit Refund 生命周期处理，不能普通修改或删除";
    }
    if (LOAN_TYPES.contains(type)) {
      return "贷款资金/融资消费只能通过 Loan 生命周期处理，不能普通修改或删除";
    }
    if (SYSTEM_STATEMENT_ORIGINS.contains(normalize(transaction.getOriginCode()))) {
      return "该交易由信用卡账单调整自动生成，只能通过账单调整生命周期处理";
    }
    return null;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
