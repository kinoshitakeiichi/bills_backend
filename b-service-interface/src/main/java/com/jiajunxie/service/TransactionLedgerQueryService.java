package com.jiajunxie.service;

import com.jiajunxie.domain.transaction.TransactionLedgerPage;
import com.jiajunxie.domain.transaction.TransactionLedgerQueryCriteria;

public interface TransactionLedgerQueryService {
  TransactionLedgerPage query(TransactionLedgerQueryCriteria criteria);
}
