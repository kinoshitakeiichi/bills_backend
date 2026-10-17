package com.jiajunxie.service;

import com.jiajunxie.vo.CreditCardStatementUpdateVo;
import com.jiajunxie.vo.CreditCardStatementVo;
import java.time.LocalDate;
import java.util.List;

public interface CreditCardStatementService {
  CreditCardStatementVo generate(String accountId, LocalDate referenceDate);
  CreditCardStatementVo get(String statementId);
  List<CreditCardStatementVo> list(String accountId, String statusCode);
  List<CreditCardStatementVo> listPayable(String accountId);
  CreditCardStatementVo updateOpenDates(String statementId, CreditCardStatementUpdateVo update);
  CreditCardStatementVo confirm(String statementId);
  CreditCardStatementVo reopen(String statementId, String reason);
  CreditCardStatementVo cancel(String statementId);
}
