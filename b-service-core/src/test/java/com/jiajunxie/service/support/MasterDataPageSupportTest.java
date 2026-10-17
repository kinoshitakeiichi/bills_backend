package com.jiajunxie.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.dto.PageQuery;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MasterDataPageSupportTest {
  @Test
  void rejectsSortFieldsOutsideWhitelist() {
    PageQuery query = new PageQuery(1, 20, null, "drop table", "desc");
    assertEquals("sortOrder", MasterDataPageSupport.allowedSortField(
        query, Set.of("sortOrder", "name"), "sortOrder"));
    assertTrue(MasterDataPageSupport.descending(query));
  }

  @Test
  void acceptsWhitelistedSortField() {
    PageQuery query = new PageQuery(1, 20, null, "name", "asc");
    assertEquals("name", MasterDataPageSupport.allowedSortField(
        query, Set.of("sortOrder", "name"), "sortOrder"));
    assertFalse(MasterDataPageSupport.descending(query));
  }
}
