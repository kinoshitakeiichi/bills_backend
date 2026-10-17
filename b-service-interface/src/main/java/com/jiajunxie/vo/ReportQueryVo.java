package com.jiajunxie.vo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public record ReportQueryVo(
    LocalDate dateFrom,
    LocalDate dateTo,
    String granularity,
    String baseCurrency,
    List<String> accountIds,
    List<String> categoryIds,
    List<String> personIds,
    ZoneId timezone) {

  private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Tokyo");

  public ReportQueryVo {
    accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
    categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
    personIds = personIds == null ? List.of() : List.copyOf(personIds);
    timezone = timezone == null ? DEFAULT_TIMEZONE : timezone;
  }

  public ReportQueryVo(
      LocalDate dateFrom,
      LocalDate dateTo,
      String granularity,
      String baseCurrency,
      List<String> accountIds,
      List<String> categoryIds,
      List<String> personIds) {
    this(
        dateFrom,
        dateTo,
        granularity,
        baseCurrency,
        accountIds,
        categoryIds,
        personIds,
        DEFAULT_TIMEZONE);
  }
}
