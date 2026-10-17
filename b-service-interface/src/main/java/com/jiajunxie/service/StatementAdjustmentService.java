package com.jiajunxie.service;

import com.jiajunxie.vo.StatementAdjustmentCreateVo;
import com.jiajunxie.vo.StatementAdjustmentSummaryVo;
import com.jiajunxie.vo.StatementAdjustmentVo;
import java.util.List;

public interface StatementAdjustmentService {
  StatementAdjustmentSummaryVo summarize(String statementId);
  List<StatementAdjustmentVo> list(String statementId);
  StatementAdjustmentVo create(String statementId, StatementAdjustmentCreateVo command);
  StatementAdjustmentVo reverse(String statementId, String adjustmentId);
}
