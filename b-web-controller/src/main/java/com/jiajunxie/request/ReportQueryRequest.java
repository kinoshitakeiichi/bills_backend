package com.jiajunxie.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record ReportQueryRequest(
    @NotNull LocalDate dateFrom,
    @NotNull LocalDate dateTo,
    @NotBlank String granularity,
    @NotBlank String baseCurrency,
    List<String> accountIds,
    List<String> categoryIds,
    List<String> personIds,
    String timezone) {}
