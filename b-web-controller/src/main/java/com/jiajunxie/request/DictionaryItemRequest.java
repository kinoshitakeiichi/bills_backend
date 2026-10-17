package com.jiajunxie.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictionaryItemRequest {
  @NotBlank(message = "字典项编码不能为空")
  @Size(max = 50, message = "字典项编码不能超过50个字符")
  private String itemCode;

  @NotBlank(message = "字典项名称不能为空")
  @Size(max = 100, message = "字典项名称不能超过100个字符")
  private String itemName;

  private String description;

  @Min(value = 0, message = "排序不能小于0")
  @Max(value = 999999, message = "排序不能大于999999")
  private Integer sortOrder;

  private Boolean enabled;
  private String metadataJson;
}
