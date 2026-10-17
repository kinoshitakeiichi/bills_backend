package com.jiajunxie.service.impl;

import com.jiajunxie.domain.transaction.TransactionNature;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TransactionNatureResolver {
  public TransactionNature resolve(String transactionTypeCode) {
    String type = normalize(transactionTypeCode);
    return switch (type) {
      case "INCOME" -> TransactionNature.INCOME;
      case "REFUND", "CREDIT_REFUND" -> TransactionNature.REFUND;
      case "EXPENSE", "CREDIT_PURCHASE", "LOAN_PURCHASE" -> TransactionNature.EXPENSE;
      case "TRANSFER" -> TransactionNature.INTERNAL_TRANSFER;
      case "CREDIT_PAYMENT", "LOAN_PAYMENT" -> TransactionNature.LIABILITY_PAYMENT;
      case "LOAN_DISBURSEMENT" -> TransactionNature.FINANCING;
      case "EXCHANGE" -> TransactionNature.FX_EXCHANGE;
      default -> TransactionNature.OTHER;
    };
  }

  public String flowDirection(TransactionNature nature) {
    return switch (nature) {
      case INCOME, REFUND -> "IN";
      case EXPENSE -> "OUT";
      case INTERNAL_TRANSFER -> "INTERNAL";
      case LIABILITY_PAYMENT -> "LIABILITY_PAYMENT";
      case FINANCING -> "FINANCING";
      case FX_EXCHANGE -> "FX";
      case OTHER -> "OTHER";
    };
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
