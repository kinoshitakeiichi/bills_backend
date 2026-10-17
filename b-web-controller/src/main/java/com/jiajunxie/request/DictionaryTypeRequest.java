package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictionaryTypeRequest {
  @NotBlank(message = "字典类型编码不能为空")
  @Size(max = 50, message = "字典类型编码不能超过50个字符")
  private String typeCode;

  @NotBlank(message = "字典类型名称不能为空")
  @Size(max = 100, message = "字典类型名称不能超过100个字符")
  private String typeName;

  private String description;
  private Boolean enabled;
}
