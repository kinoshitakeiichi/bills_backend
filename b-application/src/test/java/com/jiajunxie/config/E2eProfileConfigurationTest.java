package com.jiajunxie.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class E2eProfileConfigurationTest {

  @Test
  void e2eProfileUsesDedicatedDatabaseAndValidatesSchema() throws IOException {
    String yaml =
        new ClassPathResource("application-e2e.yml")
            .getContentAsString(StandardCharsets.UTF_8);

    assertTrue(yaml.contains("mydb_e2e"));
    assertTrue(yaml.contains("ddl-auto: validate"));
    assertTrue(yaml.contains("open-in-view: false"));
    assertTrue(yaml.contains("flyway:"));
    assertTrue(yaml.contains("enabled: true"));
  }
}
