package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.domain.transaction.TransactionLedgerRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TransactionLedgerRegexSearchTest {

  private final TransactionLedgerQueryServiceImpl service =
      new TransactionLedgerQueryServiceImpl(
          null, null, null, null, null, null, null, null, null);

  @Test
  void negativeLookaheadSeesTheWholeTransactionSearchDocument() {
    TransactionLedgerRow row = row("Amazon", "Kindo");
    Pattern pattern = Pattern.compile("(?=.*Amazon)(?!.*(Kindo))", Pattern.CASE_INSENSITIVE);

    assertFalse(service.matchesRegex(row, pattern));
  }

  @Test
  void positiveLookaheadsCanSpanDifferentSearchableFields() {
    TransactionLedgerRow row = row("Amazon", "Kindle");
    Pattern pattern = Pattern.compile("(?=.*Amazon)(?=.*Kindle)", Pattern.CASE_INSENSITIVE);

    assertTrue(service.matchesRegex(row, pattern));
  }

  @Test
  void lineBreaksInsideAFieldDoNotSplitTheRegexSearchDocument() {
    TransactionLedgerRow row = row("Amazon", "order\nKindo");
    Pattern pattern = Pattern.compile("(?=.*Amazon)(?!.*Kindo)", Pattern.CASE_INSENSITIVE);

    assertFalse(service.matchesRegex(row, pattern));
  }

  private TransactionLedgerRow row(String merchant, String remark) {
    return new TransactionLedgerRow(
        "tx-1",
        Instant.parse("2026-08-20T00:00:00Z"),
        Instant.parse("2026-08-20T00:00:00Z"),
        "EXPENSE",
        "支出",
        null,
        null,
        merchant,
        remark,
        null,
        "其他物品",
        "JPY",
        BigDecimal.TEN,
        "OUT",
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        false,
        null);
  }
}
