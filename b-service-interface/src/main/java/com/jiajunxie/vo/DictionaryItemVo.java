package com.jiajunxie.vo;

import lombok.Data;

@Data
public class DictionaryItemVo {
  private String dictionaryItemId;
  private String dictionaryTypeId;
  private String typeCode;
  private String itemCode;
  private String itemName;
  private String description;
  private Integer sortOrder;
  private Boolean enabled;
  private Boolean systemItem;
  private String metadataJson;
}
