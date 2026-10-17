package com.jiajunxie.service;

import com.jiajunxie.vo.FxLegacyRebuildReportVo;
import com.jiajunxie.vo.FxLegacyRebuildRequestVo;
import java.util.List;

public interface FxLegacyRebuildService {
  List<FxLegacyRebuildReportVo> preview(FxLegacyRebuildRequestVo request);
  List<FxLegacyRebuildReportVo> apply(FxLegacyRebuildRequestVo request);
}
