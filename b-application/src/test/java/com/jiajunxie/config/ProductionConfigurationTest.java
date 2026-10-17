package com.jiajunxie.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigurationTest {

  @Test
  void datasourceConfigurationUsesComposeCompatibleEnvironmentVariables() throws IOException {
    String yaml = read("application.yml");

    assertTrue(yaml.contains("${SPRING_DATASOURCE_URL}"));
    assertTrue(yaml.contains("${SPRING_DATASOURCE_USERNAME}"));
    assertTrue(yaml.contains("${SPRING_DATASOURCE_PASSWORD}"));
  }

  @Test
  void productionProfileDisablesSqlDebugOutput() throws IOException {
    String yaml = read("application-prod.yml");
    assertTrue(yaml.contains("show-sql: false"));
    assertFalse(yaml.contains("org.hibernate.SQL: debug"));
    assertFalse(yaml.contains("org.hibernate.tool.schema: debug"));
  }

  private String read(String path) throws IOException {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
  }
}
