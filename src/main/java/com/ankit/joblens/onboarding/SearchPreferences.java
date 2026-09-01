package com.ankit.joblens.onboarding;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchPreferences(
    @NotBlank String targetRoles,
    String targetDomains,
    @NotBlank String primaryLocation,
    @NotBlank String keywords,
    @NotBlank String searchMarkets,
    @Min(1) @Max(20) int maxPages,
    @NotBlank String employmentPreference,
    @NotBlank String workPreference) {

  public SearchPreferences {
    targetDomains = targetDomains == null ? "" : targetDomains.trim();
  }

  public java.util.List<SearchTarget> targets() {
    return SearchTarget.parse(searchMarkets);
  }
}
