package com.jiajunxie.response;

import lombok.Data;

@Data
public class DictionaryTypeResponse {
  private String dictionaryTypeId;
  private String typeCode;
  private String typeName;
  private String description;
  private Boolean enabled;
  private Long itemCount;
}
