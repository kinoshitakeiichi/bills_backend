package com.jiajunxie.service.impl;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.CategoryConvertServiceMapper;
import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.CategoryService;
import com.jiajunxie.service.support.MasterDataPageSupport;
import com.jiajunxie.vo.CategoryManagementTreeVo;
import com.jiajunxie.vo.CategoryOptionVo;
import com.jiajunxie.vo.CategoryTreeVo;
import com.jiajunxie.vo.CategoryVo;
import com.jiajunxie.vo.PageResultVo;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
  private static final String ROOT_KEY = "ROOT";

  private final CategoryMapper categoryMapper;
  private final TransactionMapper transactionMapper;
  private final CategoryConvertServiceMapper categoryConvertMapper;

  @Override
  public List<CategoryVo> listCategories() {
    return categoryConvertMapper.toVos(
        categoryMapper.selectList(
            Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getCategoryTypeCode)
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getCategoryName)));
  }

  @Override
  public PageResultVo<CategoryVo> pageCategories(
      PageQuery query, String typeCode, Boolean enabled) {
    PageQuery safe = query == null ? new PageQuery(null, null, null, null, null) : query;
    String sortField =
        MasterDataPageSupport.allowedSortField(
            safe,
            Set.of(
                "categoryCode",
                "categoryName",
                "categoryTypeCode",
                "sortOrder",
                "createdAt",
                "updatedAt"),
            "sortOrder");
    boolean asc = !MasterDataPageSupport.descending(safe);

    var wrapper =
        Wrappers.<Category>lambdaQuery()
            .eq(enabled != null, Category::getEnabled, enabled)
            .eq(
                StringUtils.hasText(typeCode),
                Category::getCategoryTypeCode,
                StringUtils.hasText(typeCode) ? normalizeCode(typeCode) : null)
            .and(
                StringUtils.hasText(safe.normalizedKeyword()),
                w ->
                    w.like(Category::getCategoryCode, safe.normalizedKeyword())
                        .or()
                        .like(Category::getCategoryName, safe.normalizedKeyword()));
    switch (sortField) {
      case "categoryCode" -> wrapper.orderBy(true, asc, Category::getCategoryCode);
      case "categoryName" -> wrapper.orderBy(true, asc, Category::getCategoryName);
      case "categoryTypeCode" -> wrapper.orderBy(true, asc, Category::getCategoryTypeCode);
      case "createdAt" -> wrapper.orderBy(true, asc, Category::getCreatedAt);
      case "updatedAt" -> wrapper.orderBy(true, asc, Category::getUpdatedAt);
      default ->
          wrapper.orderBy(true, asc, Category::getSortOrder).orderByAsc(Category::getCategoryName);
    }

    var page = categoryMapper.selectPage(MasterDataPageSupport.page(safe), wrapper);
    return MasterDataPageSupport.result(page, categoryConvertMapper::toVo);
  }

  @Override
  public List<CategoryOptionVo> listOptions(String typeCode) {
    List<Category> categories = selectCategories(typeCode, true);
    Map<String, Category> byId = indexById(categories);
    return categories.stream()
        .filter(category -> isEffectivelyEnabled(category, byId))
        .map(item -> new CategoryOptionVo(item.getCategoryId(), pathLabel(item, byId)))
        .toList();
  }

  @Override
  public List<CategoryTreeVo> listTree(String typeCode) {
    List<Category> categories = selectCategories(typeCode, true);
    Map<String, Category> byId = indexById(categories);
    List<Category> effective =
        categories.stream().filter(category -> isEffectivelyEnabled(category, byId)).toList();
    return buildBusinessTree(groupChildren(effective), ROOT_KEY);
  }

  @Override
  public List<CategoryManagementTreeVo> listManagementTree(String typeCode) {
    List<Category> categories = selectCategories(typeCode, true);
    return buildManagementTree(groupChildren(categories), ROOT_KEY);
  }

  @Override
  @Transactional
  public int createCategory(CategoryVo categoryVo) {
    validateBasic(categoryVo);
    validateParent(categoryVo, null, null);
    ensureCodeUnique(categoryVo.getCategoryCode(), null);
    ensureSiblingNameUnique(categoryVo.getParentId(), categoryVo.getCategoryName(), null);

    Category entity = categoryConvertMapper.toEntity(categoryVo);
    Instant now = Instant.now();
    entity.setCategoryId(UUID.randomUUID().toString());
    normalizeParent(entity);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return categoryMapper.insert(entity);
  }

  @Override
  @Transactional
  public int updateCategory(String categoryId, CategoryVo categoryVo) {
    Category existing = requireCategory(categoryId);
    validateBasic(categoryVo);
    if (!existing.getCategoryTypeCode().equalsIgnoreCase(categoryVo.getCategoryTypeCode())) {
      Long referenced =
          transactionMapper.selectCount(
              Wrappers.<Transaction>lambdaQuery().eq(Transaction::getCategoryId, categoryId));
      if (referenced != null && referenced > 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "分类已被历史交易引用，不能修改收入/支出类型；请新建分类并停用旧分类");
      }
    }
    validateParent(categoryVo, categoryId, existing);
    ensureCodeUnique(categoryVo.getCategoryCode(), categoryId);
    ensureSiblingNameUnique(categoryVo.getParentId(), categoryVo.getCategoryName(), categoryId);

    categoryConvertMapper.updateEntity(categoryVo, existing);
    existing.setCategoryId(categoryId);
    normalizeParent(existing);
    existing.setUpdatedAt(Instant.now());
    return categoryMapper.updateById(existing);
  }

  @Override
  @Transactional
  public int setEnabled(String categoryId, boolean enabled) {
    Category category = requireCategory(categoryId);
    if (enabled) {
      // An enabled category must have an entirely enabled ancestor chain. This keeps business trees
      // and transaction validation consistent without relying on front-end filtering.
      ensureAncestorsEnabled(category.getParentId());
      category.setEnabled(true);
      category.setUpdatedAt(Instant.now());
      return categoryMapper.updateById(category);
    }

    // Disabling a branch is intentionally cascading. Otherwise enabled descendants under a disabled
    // parent become unreachable in the business tree and can still be submitted by stale clients.
    List<Category> all = selectCategories(null, true);
    Map<String, List<Category>> children = groupChildren(all);
    List<Category> subtree = collectSubtree(category, children);
    Instant now = Instant.now();
    int affected = 0;
    for (Category node : subtree) {
      if (Boolean.TRUE.equals(node.getEnabled())) {
        node.setEnabled(false);
        node.setUpdatedAt(now);
        affected += categoryMapper.updateById(node);
      }
    }
    return affected;
  }

  @Override
  @Transactional
  public int removeCategory(String categoryId) {
    // Category category = requireCategory(categoryId);
    if (countChildren(categoryId) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "存在子分类，请先移动或删除子分类");
    }
    Long transactionCount =
        transactionMapper.selectCount(
            Wrappers.<Transaction>lambdaQuery().eq(Transaction::getCategoryId, categoryId));
    if (transactionCount != null && transactionCount > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "分类已被历史交易引用，请停用而不是删除");
    }
    return categoryMapper.deleteById(categoryId);
  }

  private List<Category> selectCategories(String typeCode, boolean includeDisabled) {
    var query = Wrappers.<Category>lambdaQuery();
    if (!includeDisabled) query.eq(Category::getEnabled, true);
    if (StringUtils.hasText(typeCode)) {
      query.eq(Category::getCategoryTypeCode, normalizeCode(typeCode));
    }
    query
        .orderByAsc(Category::getCategoryTypeCode)
        .orderByAsc(Category::getSortOrder)
        .orderByAsc(Category::getCategoryName);
    return categoryMapper.selectList(query);
  }

  private List<CategoryTreeVo> buildBusinessTree(
      Map<String, List<Category>> children, String parentId) {
    return children.getOrDefault(parentId, List.of()).stream()
        .map(
            category -> {
              List<CategoryTreeVo> childNodes =
                  buildBusinessTree(children, category.getCategoryId());
              return new CategoryTreeVo(
                  category.getCategoryId(),
                  category.getCategoryName(),
                  category.getCategoryCode(),
                  category.getCategoryTypeCode(),
                  category.getIconCode(),
                  childNodes.isEmpty(),
                  childNodes);
            })
        .toList();
  }

  private List<CategoryManagementTreeVo> buildManagementTree(
      Map<String, List<Category>> children, String parentId) {
    return children.getOrDefault(parentId, List.of()).stream()
        .map(
            category -> {
              List<CategoryManagementTreeVo> childNodes =
                  buildManagementTree(children, category.getCategoryId());
              return new CategoryManagementTreeVo(
                  category.getCategoryId(),
                  category.getCategoryName(),
                  category.getCategoryCode(),
                  category.getCategoryTypeCode(),
                  category.getIconCode(),
                  category.getParentId(),
                  category.getSortOrder(),
                  Boolean.TRUE.equals(category.getEnabled()),
                  childNodes.isEmpty(),
                  childNodes);
            })
        .toList();
  }

  private Map<String, List<Category>> groupChildren(List<Category> categories) {
    Map<String, List<Category>> children = new LinkedHashMap<>();
    for (Category category : categories) {
      String parentId = category.getParentId();
      children
          .computeIfAbsent(parentId == null ? ROOT_KEY : parentId, key -> new ArrayList<>())
          .add(category);
    }
    return children;
  }

  private Map<String, Category> indexById(List<Category> categories) {
    Map<String, Category> byId = new HashMap<>();
    categories.forEach(item -> byId.put(item.getCategoryId(), item));
    return byId;
  }

  private List<Category> collectSubtree(Category root, Map<String, List<Category>> children) {
    List<Category> result = new ArrayList<>();
    Deque<Category> queue = new ArrayDeque<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Category current = queue.removeFirst();
      result.add(current);
      queue.addAll(children.getOrDefault(current.getCategoryId(), List.of()));
    }
    return result;
  }

  private void validateBasic(CategoryVo category) {
    if (category == null
        || !StringUtils.hasText(category.getCategoryCode())
        || !StringUtils.hasText(category.getCategoryName())
        || !StringUtils.hasText(category.getCategoryTypeCode())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分类编码、名称和类型不能为空");
    }
    category.setCategoryCode(normalizeCode(category.getCategoryCode()));
    category.setCategoryName(category.getCategoryName().trim());
    category.setCategoryTypeCode(normalizeCode(category.getCategoryTypeCode()));
    category.setParentId(normalizeNullableId(category.getParentId()));
    category.setIconCode(StringUtils.hasText(category.getIconCode()) ? category.getIconCode().trim() : null);
    if (category.getEnabled() == null) category.setEnabled(true);
    if (category.getSortOrder() == null) category.setSortOrder(0);
    if (category.getSortOrder() < 0 || category.getSortOrder() > 999999) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分类排序必须在 0 到 999999 之间");
    }
  }

  private void validateParent(CategoryVo category, String categoryId, Category existing) {
    String parentId = category.getParentId();
    if (parentId == null) {
      if (existing != null
          && !Objects.equals(existing.getCategoryTypeCode(), category.getCategoryTypeCode())
          && countChildren(existing.getCategoryId()) > 0) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "存在子分类时不能修改分类类型");
      }
      return;
    }
    if (Objects.equals(categoryId, parentId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分类不能选择自身作为父分类");
    }

    Category parent = categoryMapper.selectById(parentId);
    if (parent == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "父分类不存在：" + parentId);
    }
    if (!Objects.equals(
        normalizeCode(parent.getCategoryTypeCode()), category.getCategoryTypeCode())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "父分类与当前分类的类型必须一致");
    }
    if (Boolean.TRUE.equals(category.getEnabled())) ensureAncestorsEnabled(parentId);

    // Follow the proposed parent chain before updating it. Encountering the edited category means
    // that the move would create a cycle (A -> B -> ... -> A).
    if (categoryId != null) {
      Set<String> visited = new HashSet<>();
      Category current = parent;
      while (current != null && visited.add(current.getCategoryId())) {
        if (Objects.equals(categoryId, current.getCategoryId())) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "移动分类后不能形成循环层级");
        }
        current =
            current.getParentId() == null ? null : categoryMapper.selectById(current.getParentId());
      }
      if (current != null) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "分类层级数据存在循环，请先修复数据");
      }
    }

    if (existing != null
        && !Objects.equals(existing.getCategoryTypeCode(), category.getCategoryTypeCode())
        && countChildren(existing.getCategoryId()) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "存在子分类时不能修改分类类型");
    }
  }

  private void ensureAncestorsEnabled(String parentId) {
    String currentId = normalizeNullableId(parentId);
    Set<String> visited = new HashSet<>();
    while (currentId != null) {
      if (!visited.add(currentId)) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "分类层级数据存在循环，请先修复数据");
      }
      Category parent = categoryMapper.selectById(currentId);
      if (parent == null) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "父分类不存在：" + currentId);
      }
      if (!Boolean.TRUE.equals(parent.getEnabled())) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "父分类已停用，不能启用当前分类");
      }
      currentId = normalizeNullableId(parent.getParentId());
    }
  }

  private void ensureCodeUnique(String categoryCode, String excludedCategoryId) {
    var wrapper =
        Wrappers.<Category>lambdaQuery()
            .eq(Category::getCategoryCode, categoryCode)
            .ne(
                StringUtils.hasText(excludedCategoryId),
                Category::getCategoryId,
                excludedCategoryId);
    if (categoryMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(CommonErrorCode.DUPLICATE_DATA, "分类编码已存在：" + categoryCode);
    }
  }

  private void ensureSiblingNameUnique(
      String parentId, String categoryName, String excludedCategoryId) {
    String parentKey = parentId == null ? ROOT_KEY : parentId;
    var wrapper =
        Wrappers.<Category>lambdaQuery()
            .eq(Category::getParentKey, parentKey)
            .eq(Category::getCategoryName, categoryName)
            .ne(
                StringUtils.hasText(excludedCategoryId),
                Category::getCategoryId,
                excludedCategoryId);
    if (categoryMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(CommonErrorCode.DUPLICATE_DATA, "同级分类名称已存在：" + categoryName);
    }
  }

  private long countChildren(String categoryId) {
    Long count =
        categoryMapper.selectCount(
            Wrappers.<Category>lambdaQuery().eq(Category::getParentId, categoryId));
    return count == null ? 0L : count;
  }

  private Category requireCategory(String categoryId) {
    if (!StringUtils.hasText(categoryId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分类ID不能为空");
    }
    Category category = categoryMapper.selectById(categoryId.trim());
    if (category == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "分类不存在：" + categoryId);
    }
    return category;
  }

  private void normalizeParent(Category category) {
    String parentId = normalizeNullableId(category.getParentId());
    category.setParentId(parentId);
    category.setParentKey(parentId == null ? ROOT_KEY : parentId);
  }

  private String normalizeNullableId(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String normalizeCode(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private boolean isEffectivelyEnabled(Category category, Map<String, Category> byId) {
    if (!Boolean.TRUE.equals(category.getEnabled())) return false;
    Set<String> visited = new HashSet<>();
    Category current = category;
    while (current != null && visited.add(current.getCategoryId())) {
      if (!Boolean.TRUE.equals(current.getEnabled())) return false;
      current = current.getParentId() == null ? null : byId.get(current.getParentId());
    }
    return current == null;
  }

  private String pathLabel(Category category, Map<String, Category> byId) {
    LinkedList<String> names = new LinkedList<>();
    Set<String> visited = new HashSet<>();
    Category current = category;
    while (current != null && visited.add(current.getCategoryId())) {
      names.addFirst(current.getCategoryName());
      current = current.getParentId() == null ? null : byId.get(current.getParentId());
    }
    return String.join(" / ", names);
  }
}
