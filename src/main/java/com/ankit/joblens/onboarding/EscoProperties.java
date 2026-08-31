package com.ankit.joblens.onboarding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.esco")
public record EscoProperties(
    String baseUrl,
    String version,
    int pageSize,
    Duration timeout,
    int retryAttempts,
    Duration retryBackoff,
    boolean insecureTls) {
  public EscoProperties {
    if (pageSize < 1 || pageSize > 500) {
      throw new IllegalArgumentException("ESCO page size must be between 1 and 500");
    }
    if (retryAttempts < 1) {
      throw new IllegalArgumentException("ESCO retry attempts must be positive");
    }
  }
}
