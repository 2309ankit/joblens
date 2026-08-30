package com.ankit.joblens.discovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.adzuna")
public record AdzunaProperties(
    String appId,
    String appKey,
    String baseUrl,
    Duration timeout,
    int maxPages,
    int pageSize,
    int retryAttempts,
    Duration retryBackoff) {

  public AdzunaProperties {
    if (maxPages < 1 || pageSize < 1 || retryAttempts < 1) {
      throw new IllegalArgumentException(
          "Adzuna max-pages, page-size and retry-attempts must be positive");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Adzuna timeout must be positive");
    }
    if (retryBackoff == null || retryBackoff.isNegative()) {
      throw new IllegalArgumentException("Adzuna retry-backoff must not be negative");
    }
  }

  boolean hasCredentials() {
    return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
  }
}
