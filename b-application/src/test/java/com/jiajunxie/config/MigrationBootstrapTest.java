package com.jiajunxie.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MigrationBootstrapTest {

  @Test
  void migrationBootstrapProfileKeepsFlywayDisabledUntilBaselineIsVerified() throws IOException {
    String yaml =
        new ClassPathResource("application-migration-bootstrap.yml")
            .getContentAsString(StandardCharsets.UTF_8);

    assertTrue(yaml.contains("flyway:"));
    assertTrue(yaml.contains("enabled: false"));
    assertTrue(yaml.contains("classpath:db/migration"));
    assertTrue(yaml.contains("default-schema: bills"));
  }
}
