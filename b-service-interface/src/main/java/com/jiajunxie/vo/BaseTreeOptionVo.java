package com.jiajunxie.vo;

import java.util.List;

/** 支持任意层级的树形选项契约，Category 等树形主数据可以复用。 */
public record BaseTreeOptionVo(
    String value,
    String label,
    Boolean enabled,
    List<BaseTreeOptionVo> children) {
  public BaseTreeOptionVo {
    children = children == null ? List.of() : List.copyOf(children);
  }
}
