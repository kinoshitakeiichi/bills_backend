package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.BudgetTarget;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BudgetTargetMapper extends BaseMapper<BudgetTarget> {
  @Select("SELECT * FROM bills.b_budget_targets WHERE budget_item_id = #{budgetItemId} ORDER BY target_type_code, budget_target_id")
  List<BudgetTarget> selectByBudgetItemId(String budgetItemId);

  @Delete("DELETE FROM bills.b_budget_targets WHERE budget_item_id = #{budgetItemId}")
  int deleteByBudgetItemId(String budgetItemId);
}
