package com.jiajunxie.service.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.vo.PageResultVo;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 主数据分页公共能力。
 *
 * <p>该类只处理分页参数、排序字段白名单和分页结果包装，不包含 Person/Category/Dictionary
 * 等具体业务规则。这样可以复用分页基础设施，同时避免建立难以维护的通用 CRUD Service。
 */
public final class MasterDataPageSupport {
  private MasterDataPageSupport() {}

  public static <T> Page<T> page(PageQuery query) {
    PageQuery safe = query == null ? new PageQuery(null, null, null, null, null) : query;
    return Page.of(safe.normalizedPage(), safe.normalizedSize());
  }

  public static String allowedSortField(
      PageQuery query, Set<String> allowedFields, String defaultField) {
    if (query == null || query.normalizedSortField() == null) return defaultField;
    return allowedFields.contains(query.normalizedSortField())
        ? query.normalizedSortField()
        : defaultField;
  }

  public static boolean descending(PageQuery query) {
    return query != null && "desc".equals(query.normalizedSortOrder());
  }

  public static <S, T> PageResultVo<T> result(IPage<S> page, Function<S, T> mapper) {
    List<T> records = page.getRecords().stream().map(mapper).toList();
    return PageResultVo.of(page.getTotal(), (int) page.getCurrent(), (int) page.getSize(), records);
  }
}
