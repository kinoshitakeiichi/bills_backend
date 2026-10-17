package com.jiajunxie.response;

import java.util.List;

/** Web 层统一分页响应。 */
public record PageResponse<T>(long total, int page, int size, List<T> records) {
  public PageResponse {
    records = records == null ? List.of() : List.copyOf(records);
  }
}
