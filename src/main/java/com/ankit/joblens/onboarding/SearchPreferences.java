package com.ankit.joblens.onboarding;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchPreferences(
    @NotBlank String targetRoles,
    String targetDomains,
    @NotBlank String primaryLocation,
    String keywords,
    @NotBlank String searchMarkets,
    @Min(1) @Max(20) int maxPages,
    @NotBlank String employmentPreference,
    @NotBlank String workPreference,
    boolean excludeMyCareersFuture,
    String salaryMin,
    String salaryMax) {

  public SearchPreferences {
    targetDomains = targetDomains == null ? "" : targetDomains.trim();
    keywords = keywords == null ? "" : keywords.trim();
    salaryMin = salaryMin == null ? "" : salaryMin.trim();
    salaryMax = salaryMax == null ? "" : salaryMax.trim();
  }

  public SearchPreferences(
      String targetRoles,
      String targetDomains,
      String primaryLocation,
      String keywords,
      String searchMarkets,
      int maxPages,
      String employmentPreference,
      String workPreference) {
    this(
        targetRoles,
        targetDomains,
        primaryLocation,
        keywords,
        searchMarkets,
        maxPages,
        employmentPreference,
        workPreference,
        false,
        "",
        "");
  }

  public java.util.List<SearchTarget> targets() {
    return SearchTarget.parse(searchMarkets);
  }
}
