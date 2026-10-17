package com.jiajunxie.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {
  @NotBlank(message = "分类编码不能为空")
  @Size(max = 50, message = "分类编码不能超过50个字符")
  private String categoryCode;

  @NotBlank(message = "分类名称不能为空")
  @Size(max = 100, message = "分类名称不能超过100个字符")
  private String categoryName;

  @NotBlank(message = "分类类型不能为空")
  @Size(max = 20, message = "分类类型不能超过20个字符")
  private String categoryTypeCode;

  @Size(max = 64, message = "分类图标编码不能超过64个字符")
  private String iconCode;

  private String parentId;

  @Min(value = 0, message = "排序不能小于0")
  @Max(value = 999999, message = "排序不能大于999999")
  private Integer sortOrder;

  private Boolean enabled;
}
