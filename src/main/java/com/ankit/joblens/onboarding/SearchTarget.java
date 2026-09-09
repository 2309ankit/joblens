package com.ankit.joblens.onboarding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public record SearchTarget(String countryCode, String location) {
  public SearchTarget {
    countryCode = countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
    location = location == null ? "" : location.trim();
    if (!countryCode.matches("[A-Z]{2}")) {
      throw new IllegalArgumentException("Each search market needs a two-letter country code");
    }
    if (location.isBlank() || location.length() > 150) {
      throw new IllegalArgumentException(
          "Each search market needs a location up to 150 characters");
    }
  }

  public static List<SearchTarget> parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Add at least one search market");
    }
    var targets = new LinkedHashMap<String, SearchTarget>();
    for (String line : value.split("\\R")) {
      if (line.isBlank()) {
        continue;
      }
      String[] parts = line.split("\\|", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException(
            "Use one search market per line in the format SG | Singapore");
      }
      SearchTarget target = new SearchTarget(parts[0], parts[1]);
      String key = target.countryCode() + "|" + target.location().toLowerCase(Locale.ROOT);
      if (targets.putIfAbsent(key, target) != null) {
        throw new IllegalArgumentException("Remove duplicate search markets before activation");
      }
    }
    if (targets.isEmpty()) {
      throw new IllegalArgumentException("Add at least one search market");
    }
    if (targets.size() > 10) {
      throw new IllegalArgumentException("A maximum of 10 search markets is supported");
    }
    return List.copyOf(targets.values());
  }

  public static String format(List<SearchTarget> targets) {
    return targets.stream()
        .map(target -> target.countryCode() + " | " + target.location())
        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
  }
}
