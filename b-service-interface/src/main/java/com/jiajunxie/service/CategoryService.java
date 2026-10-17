package com.jiajunxie.service;

import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.vo.CategoryManagementTreeVo;
import com.jiajunxie.vo.CategoryOptionVo;
import com.jiajunxie.vo.CategoryTreeVo;
import com.jiajunxie.vo.CategoryVo;
import com.jiajunxie.vo.PageResultVo;
import java.util.List;

public interface CategoryService {
  List<CategoryVo> listCategories();

  PageResultVo<CategoryVo> pageCategories(PageQuery query, String typeCode, Boolean enabled);

  List<CategoryOptionVo> listOptions(String typeCode);

  /** Returns the enabled business tree used by transaction forms. */
  List<CategoryTreeVo> listTree(String typeCode);

  /** Returns the complete tree, including disabled nodes, for category maintenance. */
  List<CategoryManagementTreeVo> listManagementTree(String typeCode);

  int createCategory(CategoryVo category);

  int updateCategory(String categoryId, CategoryVo category);

  /**
   * Changes availability without deleting historical master data. Disabling a parent category also
   * disables its descendants so an enabled child can never live below a disabled ancestor.
   */
  int setEnabled(String categoryId, boolean enabled);

  /** Physically removes only an unused leaf category. */
  int removeCategory(String categoryId);
}
