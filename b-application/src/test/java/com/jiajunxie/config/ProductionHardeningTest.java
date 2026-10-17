package com.jiajunxie.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ProductionHardeningTest {

  @Test
  void productionDisablesOpenInViewAndPublicApiDocumentation() throws IOException {
    String yaml =
        new ClassPathResource("application-prod.yml")
            .getContentAsString(StandardCharsets.UTF_8);

    assertTrue(yaml.contains("open-in-view: false"));
    assertTrue(yaml.contains("api-docs:"));
    assertTrue(yaml.contains("swagger-ui:"));
    assertTrue(yaml.contains("enabled: false"));
  }

  @Test
  void baseRuntimeDisablesDevtoolsRestartByDefault() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertTrue(yaml.contains("SPRING_DEVTOOLS_RESTART_ENABLED:false"));
    assertTrue(yaml.contains("SPRING_DEVTOOLS_ADD_PROPERTIES:false"));
    assertTrue(yaml.contains("FX_INVENTORY_APPLY_ENABLED:false"));
  }

  @Test
  void migrationDirectoryContainsOnlyCurrentVersionedMigrations() {
    List<String> migrations =
        List.of(
            "db/migration/V1__baseline_live_schema.sql",
            "db/migration/V2__master_data.sql",
            "db/migration/V3__japan_business_calendar.sql",
            "db/migration/V4__financial_invariant_fx_lifecycle.sql",
            "db/migration/V5__currency_inventory_preview.sql",
            "db/migration/V6__currency_inventory_cutover_guard.sql",
            "db/migration/V7__retire_legacy_fx_position_writes.sql");

    migrations.forEach(path -> assertTrue(new ClassPathResource(path).exists()));
  }
}
