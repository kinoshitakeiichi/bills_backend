package com.jiajunxie.service;

import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.CreditCardProfileVo;
import java.time.LocalDate;

public interface CreditCardDateRuleService {
  CreditCardDatePreviewVo previewSaved(String accountId, LocalDate referenceDate);
  CreditCardDatePreviewVo previewDraft(CreditCardProfileVo profile, LocalDate referenceDate);
}
