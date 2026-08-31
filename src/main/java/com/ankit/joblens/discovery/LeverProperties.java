package com.ankit.joblens.discovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.lever")
public record LeverProperties(
    String baseUrl, Duration timeout, int pageSize, int retryAttempts, Duration retryBackoff) {

  public LeverProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("Lever base-url is required");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Lever timeout must be positive");
    }
    if (pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("Lever page-size must be between 1 and 100");
    }
    if (retryAttempts < 1) {
      throw new IllegalArgumentException("Lever retry-attempts must be positive");
    }
    if (retryBackoff == null || retryBackoff.isNegative()) {
      throw new IllegalArgumentException("Lever retry-backoff must not be negative");
    }
  }
}
