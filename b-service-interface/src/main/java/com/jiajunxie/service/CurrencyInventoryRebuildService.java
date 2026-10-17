package com.jiajunxie.service;

import com.jiajunxie.vo.CurrencyInventoryRebuildApplyRequestVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildApplyResultVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildPreviewVo;
import com.jiajunxie.vo.CurrencyInventoryRebuildRequestVo;

public interface CurrencyInventoryRebuildService {
  CurrencyInventoryRebuildPreviewVo preview(CurrencyInventoryRebuildRequestVo request);

  CurrencyInventoryRebuildApplyResultVo apply(CurrencyInventoryRebuildApplyRequestVo request);
}
