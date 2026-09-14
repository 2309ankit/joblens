package com.ankit.joblens.discovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.query-planning.llm")
public record LlmQueryPlanningProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String model,
    Duration timeout,
    int maxProfilesPerRun,
    int maxCallsPerDay,
    int maxOutputTokens,
    int maxPagesCeiling,
    String promptVersion) {

  public LlmQueryPlanningProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("Query planning base-url is required");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Query planning timeout must be positive");
    }
    if (maxProfilesPerRun < 1 || maxCallsPerDay < 1 || maxOutputTokens < 1 || maxPagesCeiling < 1) {
      throw new IllegalArgumentException("Query planning limits must be positive");
    }
    if (promptVersion == null || promptVersion.isBlank()) {
      throw new IllegalArgumentException("Query planning prompt-version is required");
    }
  }

  void requireEnabledConfiguration() {
    if (!enabled) {
      return;
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "Query planning is enabled but NEBIUS_API_KEY is not configured");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalStateException(
          "Query planning is enabled but NEBIUS_QUERY_PLANNING_MODEL is not configured");
    }
  }
}
