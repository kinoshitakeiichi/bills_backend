package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.CategoryConvertServiceMapper;
import com.jiajunxie.entity.Category;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.vo.CategoryVo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {
  @Mock CategoryMapper categoryMapper;
  @Mock TransactionMapper transactionMapper;
  @Mock CategoryConvertServiceMapper categoryConvertMapper;

  @Test
  void createRejectsDuplicateCategoryCode() {
    var service = service();
    when(categoryMapper.selectCount(any())).thenReturn(1L);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.createCategory(categoryVo("FOOD", "餐饮", "EXPENSE", null)));

    assertEquals(40901, exception.getCode());
  }

  @Test
  void createRejectsDuplicateSiblingName() {
    var service = service();
    when(categoryMapper.selectCount(any())).thenReturn(0L, 1L);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.createCategory(categoryVo("FOOD_LUNCH", "午餐", "EXPENSE", null)));

    assertEquals(40901, exception.getCode());
  }

  @Test
  void createRejectsParentWithDifferentType() {
    var service = service();
    Category parent = category("p1", "收入", "INCOME", null, true);
    when(categoryMapper.selectById("p1")).thenReturn(parent);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.createCategory(categoryVo("LUNCH", "午餐", "EXPENSE", "p1")));

    assertEquals(40000, exception.getCode());
  }

  @Test
  void updateRejectsMoveThatCreatesCycle() {
    var service = service();
    Category a = category("a", "A", "EXPENSE", null, true);
    Category b = category("b", "B", "EXPENSE", "a", true);
    when(categoryMapper.selectById("a")).thenReturn(a);
    when(categoryMapper.selectById("b")).thenReturn(b);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.updateCategory("a", categoryVo("A", "A", "EXPENSE", "b")));

    assertEquals(40000, exception.getCode());
  }

  @Test
  void disablingParentDisablesEntireSubtree() {
    var service = service();
    Category parent = category("p", "父", "EXPENSE", null, true);
    Category child = category("c", "子", "EXPENSE", "p", true);
    when(categoryMapper.selectById("p")).thenReturn(parent);
    when(categoryMapper.selectList(any())).thenReturn(List.of(parent, child));
    when(categoryMapper.updateById(any(Category.class))).thenReturn(1);

    assertEquals(2, service.setEnabled("p", false));
    assertFalse(parent.getEnabled());
    assertFalse(child.getEnabled());
  }

  @Test
  void enablingCategoryRejectsDisabledAncestor() {
    var service = service();
    Category child = category("c", "子", "EXPENSE", "p", false);
    Category parent = category("p", "父", "EXPENSE", null, false);
    when(categoryMapper.selectById("c")).thenReturn(child);
    when(categoryMapper.selectById("p")).thenReturn(parent);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.setEnabled("c", true));

    assertEquals(40900, exception.getCode());
  }

  @Test
  void removeRejectsCategoryWithChildren() {
    var service = service();
    when(categoryMapper.selectCount(any())).thenReturn(1L);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.removeCategory("p"));

    assertEquals(40900, exception.getCode());
  }

  @Test
  void removeRejectsCategoryReferencedByTransaction() {
    var service = service();
    when(categoryMapper.selectCount(any())).thenReturn(0L);
    when(transactionMapper.selectCount(any())).thenReturn(1L);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.removeCategory("c"));

    assertEquals(40900, exception.getCode());
  }

  private CategoryServiceImpl service() {
    return new CategoryServiceImpl(categoryMapper, transactionMapper, categoryConvertMapper);
  }

  private CategoryVo categoryVo(String code, String name, String type, String parentId) {
    var vo = new CategoryVo();
    vo.setCategoryCode(code);
    vo.setCategoryName(name);
    vo.setCategoryTypeCode(type);
    vo.setParentId(parentId);
    vo.setEnabled(true);
    vo.setSortOrder(0);
    return vo;
  }

  private Category category(String id, String name, String type, String parentId, boolean enabled) {
    var category = new Category();
    category.setCategoryId(id);
    category.setCategoryCode(id.toUpperCase());
    category.setCategoryName(name);
    category.setCategoryTypeCode(type);
    category.setParentId(parentId);
    category.setParentKey(parentId == null ? "ROOT" : parentId);
    category.setSortOrder(0);
    category.setEnabled(enabled);
    return category;
  }
  @Test
  void updateRejectsTypeChangeWhenCategoryHasHistoricalTransactions() {
    var service = service();
    Category existing = category("c", "餐饮", "EXPENSE", null, true);
    when(categoryMapper.selectById("c")).thenReturn(existing);
    when(transactionMapper.selectCount(any())).thenReturn(1L);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.updateCategory("c", categoryVo("FOOD", "餐饮", "INCOME", null)));

    assertEquals(40900, exception.getCode());
  }

}
