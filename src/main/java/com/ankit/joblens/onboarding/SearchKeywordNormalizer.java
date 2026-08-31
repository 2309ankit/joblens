package com.ankit.joblens.onboarding;

import java.util.regex.Pattern;

public final class SearchKeywordNormalizer {
  private static final Pattern ICT_PREFIX =
      Pattern.compile(
          "(?i)^(?:ICT|information\\s+and\\s+communications?\\s+technology)\\s+[-–—:/]*\\s*");

  private SearchKeywordNormalizer() {}

  public static String normalize(String value) {
    String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
    String withoutTaxonomyQualifier = ICT_PREFIX.matcher(trimmed).replaceFirst("").trim();
    return withoutTaxonomyQualifier.isBlank() ? trimmed : withoutTaxonomyQualifier;
  }
}
