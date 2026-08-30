package com.ankit.joblens.discovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.greenhouse")
public record GreenhouseProperties(
    String baseUrl, Duration timeout, int retryAttempts, Duration retryBackoff) {

  public GreenhouseProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("Greenhouse base-url is required");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Greenhouse timeout must be positive");
    }
    if (retryAttempts < 1) {
      throw new IllegalArgumentException("Greenhouse retry-attempts must be positive");
    }
    if (retryBackoff == null || retryBackoff.isNegative()) {
      throw new IllegalArgumentException("Greenhouse retry-backoff must not be negative");
    }
  }
}
