package com.jiajunxie.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class CategoryVo {
  private String categoryId;
  private String categoryCode;
  private String categoryName;
  private String categoryTypeCode;
  private String iconCode;
  private String parentId;
  private Integer sortOrder;
  private Boolean enabled;
  private Instant createdAt;
  private Instant updatedAt;
}
