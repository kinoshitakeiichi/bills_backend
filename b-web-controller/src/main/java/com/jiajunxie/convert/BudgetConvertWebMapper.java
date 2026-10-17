package com.jiajunxie.convert;

import com.jiajunxie.request.BudgetRequest;
import com.jiajunxie.response.BudgetActualResponse;
import com.jiajunxie.response.BudgetItemResponse;
import com.jiajunxie.response.BudgetResponse;
import com.jiajunxie.response.BudgetTargetResponse;
import com.jiajunxie.vo.BudgetActualVo;
import com.jiajunxie.vo.BudgetItemVo;
import com.jiajunxie.vo.BudgetSaveVo;
import com.jiajunxie.vo.BudgetTargetVo;
import com.jiajunxie.vo.BudgetVo;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BudgetConvertWebMapper {

  public BudgetSaveVo toSaveVo(BudgetRequest source) {
    return new BudgetSaveVo(
        source.budgetName(),
        source.periodTypeCode(),
        source.periodStart(),
        source.periodEnd(),
        source.baseCurrency(),
        source.enabled(),
        source.items().stream().map(this::toSaveItem).toList());
  }

  public BudgetResponse toResponse(BudgetVo source) {
    return new BudgetResponse(
        source.budgetId(),
        source.budgetName(),
        source.periodTypeCode(),
        source.periodStart(),
        source.periodEnd(),
        source.baseCurrency(),
        source.enabled(),
        source.items().stream().map(this::toItemResponse).toList());
  }

  public List<BudgetResponse> toResponses(List<BudgetVo> source) {
    return source.stream().map(this::toResponse).toList();
  }

  public BudgetActualResponse toActualResponse(BudgetActualVo source) {
    return new BudgetActualResponse(
        source.budgetId(),
        source.budgetName(),
        source.periodStart(),
        source.periodEnd(),
        source.baseCurrency(),
        source.items().stream().map(this::toActualItemResponse).toList());
  }

  private BudgetSaveVo.Item toSaveItem(BudgetRequest.Item source) {
    return new BudgetSaveVo.Item(
        source.budgetAmount(),
        source.currency(),
        source.rolloverEnabled(),
        source.sortOrder(),
        source.targets() == null
            ? List.of()
            : source.targets().stream().map(this::toSaveTarget).toList());
  }

  private BudgetSaveVo.Target toSaveTarget(BudgetRequest.Target source) {
    return new BudgetSaveVo.Target(
        source.targetTypeCode(),
        source.targetId(),
        source.targetValue(),
        source.includeDescendants());
  }

  private BudgetItemResponse toItemResponse(BudgetItemVo source) {
    return new BudgetItemResponse(
        source.budgetItemId(),
        source.budgetAmount(),
        source.currency(),
        source.rolloverEnabled(),
        source.sortOrder(),
        source.targets().stream().map(this::toTargetResponse).toList());
  }

  private BudgetTargetResponse toTargetResponse(BudgetTargetVo source) {
    return new BudgetTargetResponse(
        source.budgetTargetId(),
        source.targetTypeCode(),
        source.targetId(),
        source.targetValue(),
        source.includeDescendants());
  }

  private BudgetActualResponse.ItemActualResponse toActualItemResponse(
      BudgetActualVo.ItemActual source) {
    return new BudgetActualResponse.ItemActualResponse(
        source.budgetItemId(),
        source.budgetAmount(),
        source.actualAmount(),
        source.remainingAmount(),
        source.currency(),
        source.rolloverEnabled(),
        source.sortOrder(),
        source.targets().stream().map(this::toTargetResponse).toList());
  }
}
