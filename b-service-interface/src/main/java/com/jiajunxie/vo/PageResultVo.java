package com.jiajunxie.vo;

import java.util.List;

/** 统一分页结果。records 始终返回不可变且非 null 的列表。 */
public record PageResultVo<T>(long total, int page, int size, List<T> records) {
  public PageResultVo {
    records = records == null ? List.of() : List.copyOf(records);
  }

  public static <T> PageResultVo<T> of(long total, int page, int size, List<T> records) {
    return new PageResultVo<>(total, page, size, records);
  }
}
