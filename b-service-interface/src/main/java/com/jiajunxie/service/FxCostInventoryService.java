package com.jiajunxie.service;

import com.jiajunxie.vo.FxCostInventoryValuationVo;
import com.jiajunxie.vo.FxCostInventoryVo;
import java.time.LocalDate;

public interface FxCostInventoryService {

  FxCostInventoryVo get(String accountId);

  FxCostInventoryValuationVo realtime(String accountId);

  FxCostInventoryValuationVo reference(
      String accountId, LocalDate valuationDate, String rateSourceCode);
}
