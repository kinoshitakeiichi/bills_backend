package com.jiajunxie.vo;

import lombok.Data;

@Data
public class DictionaryTypeVo {
  private String dictionaryTypeId;
  private String typeCode;
  private String typeName;
  private String description;
  private Boolean enabled;
  private Long itemCount;
}
