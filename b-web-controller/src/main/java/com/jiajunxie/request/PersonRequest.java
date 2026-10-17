package com.jiajunxie.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PersonRequest {
  @NotBlank(message = "人员编码不能为空")
  @Size(max = 30, message = "人员编码不能超过30个字符")
  private String personCode;

  @NotBlank(message = "人员名称不能为空")
  @Size(max = 50, message = "人员名称不能超过50个字符")
  private String personName;

  private Boolean enabled;
  @Min(value = 0, message = "排序不能小于0")
  @Max(value = 999999, message = "排序不能大于999999")
  private Integer sortOrder;
}
