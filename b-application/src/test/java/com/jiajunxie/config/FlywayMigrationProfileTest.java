package com.jiajunxie.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayMigrationProfileTest {

  @Test
  void migrationProfileEnablesFlywayAndChangesHibernateToValidate() throws IOException {
    String yaml =
        new ClassPathResource("application-migration.yml")
            .getContentAsString(StandardCharsets.UTF_8);

    assertTrue(yaml.contains("enabled: true"));
    assertTrue(yaml.contains("baseline-on-migrate: true"));
    assertTrue(yaml.contains("baseline-version: \"1\""));
    assertTrue(yaml.contains("clean-disabled: true"));
    assertTrue(yaml.contains("defer-datasource-initialization: false"));
    assertTrue(yaml.contains("ddl-auto: validate"));
  }

  @Test
  void versionedMigrationResourcesExist() throws IOException {
    assertTrue(new ClassPathResource("db/migration/V1__baseline_live_schema.sql").exists());
    assertTrue(new ClassPathResource("db/migration/V2__master_data.sql").exists());
    assertTrue(new ClassPathResource("db/migration/V3__japan_business_calendar.sql").exists());
    assertTrue(new ClassPathResource("db/migration/V4__financial_invariant_fx_lifecycle.sql").exists());
    assertTrue(new ClassPathResource("db/migration/V5__currency_inventory_preview.sql").exists());
    assertTrue(new ClassPathResource("db/migration/V6__currency_inventory_cutover_guard.sql").exists());
    assertTrue(new ClassPathResource("db/migration/V7__retire_legacy_fx_position_writes.sql").exists());
  }
}
