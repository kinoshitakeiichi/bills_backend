package com.jiajunxie.service;

import com.jiajunxie.vo.BudgetActualVo;
import com.jiajunxie.vo.BudgetSaveVo;
import com.jiajunxie.vo.BudgetVo;
import java.time.ZoneId;
import java.util.List;

public interface BudgetService {
  List<BudgetVo> listBudgets();

  BudgetVo getBudget(String budgetId);

  String createBudget(BudgetSaveVo request);

  int updateBudget(String budgetId, BudgetSaveVo request);

  int removeBudget(String budgetId);

  BudgetActualVo getActual(String budgetId, ZoneId timezone);
}
