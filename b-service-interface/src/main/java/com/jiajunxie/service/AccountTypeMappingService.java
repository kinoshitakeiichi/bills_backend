package com.jiajunxie.service;

import com.jiajunxie.vo.AccountTypeMappingVo;
import com.jiajunxie.vo.AccountTypeOptionVo;
import java.util.List;

/** Structured configuration for the ACCOUNT_TYPE -> ACCOUNT_CLASS relationship. */
public interface AccountTypeMappingService {

  List<AccountTypeMappingVo> listMappings();

  List<AccountTypeOptionVo> listOptions(String keyword);

  /** Resolves a class for a new business operation; the account type must be enabled. */
  String resolveAccountClassCode(String accountTypeCode);

  /** Resolves a configured class, optionally allowing an already-used disabled account type. */
  String resolveAccountClassCode(String accountTypeCode, boolean requireEnabledType);

  int upsertMapping(String accountTypeCode, String accountClassCode);
}
