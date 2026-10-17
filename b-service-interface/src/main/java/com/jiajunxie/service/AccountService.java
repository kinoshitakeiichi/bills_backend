package com.jiajunxie.service;

import java.util.List;
import com.jiajunxie.vo.AccountIdLabelVo;
import com.jiajunxie.vo.AccountOptionVo;
import com.jiajunxie.vo.AccountInfoVo;
import com.jiajunxie.vo.AccountVo;

public interface AccountService {

  Integer addAccount(AccountVo accountVo);

  List<AccountInfoVo> cardList();

  List<AccountIdLabelVo> idLabelList();

  List<AccountOptionVo> optionList(String accountClassCode, String accountTypeCode, Boolean baseOnly);

  Integer updateMasterData(AccountVo accountVo);

  Integer setEnabled(String accountId, boolean enabled);

  Integer removeAccount(String accountId);
}
