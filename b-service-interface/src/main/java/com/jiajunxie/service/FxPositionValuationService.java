package com.jiajunxie.service;

import com.jiajunxie.vo.FxPositionValuationVo;
import java.time.LocalDate;

public interface FxPositionValuationService {

  FxPositionValuationVo realtime(String accountId);

  FxPositionValuationVo reference(
      String accountId, LocalDate valuationDate, String rateSourceCode);
}
