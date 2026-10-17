package com.jiajunxie.service;

import com.jiajunxie.vo.AccountTransactionTemplateSaveVo;
import com.jiajunxie.vo.AccountTransactionTemplateVo;
import java.util.List;

public interface AccountTransactionTemplateService {
  List<AccountTransactionTemplateVo> list(String accountId);
  String create(String accountId, AccountTransactionTemplateSaveVo request);
  int update(String accountId, String templateId, AccountTransactionTemplateSaveVo request);
  int remove(String accountId, String templateId);
}
