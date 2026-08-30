package com.ankit.joblens.discovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.jooble")
public record JoobleProperties(
    String apiKey,
    String baseUrl,
    Duration timeout,
    int pageSize,
    int retryAttempts,
    Duration retryBackoff) {

  public JoobleProperties {
    if (pageSize < 1 || retryAttempts < 1) {
      throw new IllegalArgumentException("Jooble page-size and retry-attempts must be positive");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Jooble timeout must be positive");
    }
    if (retryBackoff == null || retryBackoff.isNegative()) {
      throw new IllegalArgumentException("Jooble retry-backoff must not be negative");
    }
  }

  public boolean hasCredentials() {
    return apiKey != null && !apiKey.isBlank();
  }
}
