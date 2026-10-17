package com.jiajunxie.service;

import com.jiajunxie.vo.FxExchangeCreatedVo;
import com.jiajunxie.vo.FxExchangeCreateVo;
import com.jiajunxie.vo.FxPositionVo;

public interface FxExchangeService {

  FxExchangeCreatedVo create(FxExchangeCreateVo command);

  FxPositionVo getPosition(String accountId);
}
