package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.BudgetItem;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BudgetItemMapper extends BaseMapper<BudgetItem> {
  @Select("SELECT * FROM bills.b_budget_items WHERE budget_id = #{budgetId} ORDER BY sort_order, budget_item_id")
  List<BudgetItem> selectByBudgetId(String budgetId);

  @Delete("DELETE FROM bills.b_budget_items WHERE budget_id = #{budgetId}")
  int deleteByBudgetId(String budgetId);
}
