package com.jiajunxie.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BudgetRequest(
    @NotBlank(message = "预算名称不能为空")
        @Size(max = 100, message = "预算名称不能超过100个字符")
        String budgetName,
    @NotBlank(message = "预算周期类型不能为空")
        @Size(max = 30, message = "预算周期类型不能超过30个字符")
        String periodTypeCode,
    @NotNull(message = "预算开始日期不能为空") LocalDate periodStart,
    @NotNull(message = "预算结束日期不能为空") LocalDate periodEnd,
    @NotBlank(message = "预算基准币种不能为空")
        @Size(max = 10, message = "预算基准币种不能超过10个字符")
        String baseCurrency,
    Boolean enabled,
    @NotEmpty(message = "预算至少需要一个预算项") List<@Valid Item> items) {

  public record Item(
      @NotNull(message = "预算金额不能为空")
          @DecimalMin(value = "0", inclusive = true, message = "预算金额不能小于0")
          BigDecimal budgetAmount,
      @NotBlank(message = "预算项币种不能为空")
          @Size(max = 10, message = "预算项币种不能超过10个字符")
          String currency,
      Boolean rolloverEnabled,
      Integer sortOrder,
      List<@Valid Target> targets) {}

  public record Target(
      @NotBlank(message = "预算目标类型不能为空")
          @Size(max = 30, message = "预算目标类型不能超过30个字符")
          String targetTypeCode,
      @Size(max = 36, message = "预算目标ID不能超过36个字符") String targetId,
      @Size(max = 150, message = "预算目标值不能超过150个字符") String targetValue,
      Boolean includeDescendants) {}
}
