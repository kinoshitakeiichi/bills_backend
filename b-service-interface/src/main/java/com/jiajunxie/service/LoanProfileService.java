package com.jiajunxie.service;

import com.jiajunxie.vo.LoanProfileVo;
import com.jiajunxie.vo.LoanRateHistoryVo;
import java.util.List;

public interface LoanProfileService {
  LoanProfileVo getProfile(String accountId);
  int upsertProfile(String accountId, LoanProfileVo vo);
  List<LoanRateHistoryVo> listRateHistory(String accountId);
  int addRateHistory(String accountId, LoanRateHistoryVo vo);
}
