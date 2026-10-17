package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReportCategoryOrderContractTest {

  @Test
  void expenseCategoryTreeOrdersSiblingsByAmountDescending() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/jiajunxie/service/impl/ReportServiceImpl.java"));
    assertTrue(source.contains("amounts.getOrDefault(category.getCategoryId(), BigDecimal.ZERO)"));
    assertTrue(source.contains("Comparator.reverseOrder()"));
  }
}
