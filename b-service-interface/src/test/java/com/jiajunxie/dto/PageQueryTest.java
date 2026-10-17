package com.jiajunxie.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PageQueryTest {
  @Test
  void normalizesDefaultsAndCapsPageSize() {
    PageQuery query = new PageQuery(0, 999, "  ", "  ", null);
    assertEquals(1, query.normalizedPage());
    assertEquals(200, query.normalizedSize());
    assertNull(query.normalizedKeyword());
    assertNull(query.normalizedSortField());
    assertEquals("asc", query.normalizedSortOrder());
  }

  @Test
  void normalizesKeywordAndDescendingOrder() {
    PageQuery query = new PageQuery(2, 50, "  abc  ", "personName", "DESC");
    assertEquals("abc", query.normalizedKeyword());
    assertEquals("personName", query.normalizedSortField());
    assertEquals("desc", query.normalizedSortOrder());
  }
}
