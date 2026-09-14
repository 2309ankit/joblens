package com.ankit.joblens.intelligence;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.ranking.nvidia")
public record NvidiaRankingProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String model,
    Duration timeout,
    int maxJobsPerRun,
    int maxCallsPerDay,
    int maxOutputTokens,
    String promptVersion) {

  public NvidiaRankingProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("NVIDIA ranking base-url is required");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("NVIDIA ranking timeout must be positive");
    }
    if (maxJobsPerRun < 1 || maxCallsPerDay < 1 || maxOutputTokens < 1) {
      throw new IllegalArgumentException("NVIDIA ranking limits must be positive");
    }
    if (promptVersion == null || promptVersion.isBlank()) {
      throw new IllegalArgumentException("NVIDIA ranking prompt-version is required");
    }
  }

  void requireEnabledConfiguration() {
    if (!enabled) {
      return;
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "NVIDIA ranking is enabled but NEBIUS_API_KEY is not configured");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalStateException(
          "NVIDIA ranking is enabled but NEBIUS_MODEL is not configured");
    }
  }
}
