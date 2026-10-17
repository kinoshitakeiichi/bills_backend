package com.jiajunxie.dto;

import java.util.Locale;

/**
 * 主数据列表的统一分页查询契约。
 *
 * <p>这里只负责参数标准化，不负责决定哪些字段允许排序。允许排序字段必须由具体 Service
 * 使用白名单声明，禁止把前端 sortField 直接拼接进 SQL。
 */
public record PageQuery(
    Integer page,
    Integer size,
    String keyword,
    String sortField,
    String sortOrder) {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 200;

  public int normalizedPage() {
    return page == null || page < 1 ? DEFAULT_PAGE : page;
  }

  public int normalizedSize() {
    if (size == null || size < 1) return DEFAULT_SIZE;
    return Math.min(size, MAX_SIZE);
  }

  public String normalizedKeyword() {
    return keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  public String normalizedSortField() {
    return sortField == null || sortField.isBlank() ? null : sortField.trim();
  }

  public String normalizedSortOrder() {
    if (sortOrder == null || sortOrder.isBlank()) return "asc";
    return "desc".equals(sortOrder.trim().toLowerCase(Locale.ROOT)) ? "desc" : "asc";
  }
}
