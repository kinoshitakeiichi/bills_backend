package com.jiajunxie.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class PersonVo {
  private String personId;
  private String personCode;
  private String personName;
  private Boolean enabled;
  private Integer sortOrder;
  private Instant createdAt;
  private Instant updatedAt;
}
