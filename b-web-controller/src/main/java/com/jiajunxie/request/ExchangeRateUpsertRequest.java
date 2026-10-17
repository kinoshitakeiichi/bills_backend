package com.jiajunxie.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class ExchangeRateUpsertRequest {

  @NotNull(message = "汇率日期不能为空")
  private LocalDate rateDate;

  @NotBlank(message = "基础币种不能为空")
  @Size(max = 10)
  private String baseCurrency;

  @NotBlank(message = "报价币种不能为空")
  @Size(max = 10)
  private String quoteCurrency;

  @NotNull(message = "汇率不能为空")
  @DecimalMin(value = "0", inclusive = false, message = "汇率必须大于 0")
  private BigDecimal exchangeRate;
}
