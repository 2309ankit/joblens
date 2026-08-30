package com.ankit.joblens.searchprofile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.batch.infrastructure.item.ItemProcessor;

public class SearchProfileProcessor implements ItemProcessor<SearchProfileCsvRow, SearchProfile> {

  private static final Set<String> SUPPORTED_SOURCES = Set.of("ADZUNA", "GREENHOUSE");
  private static final Set<String> SUPPORTED_EMPLOYMENT_TYPES =
      Set.of("ANY", "PERMANENT", "CONTRACT");

  private final Long failOnRow;

  public SearchProfileProcessor() {
    this(null);
  }

  public SearchProfileProcessor(Long failOnRow) {
    this.failOnRow = failOnRow;
  }

  @Override
  public SearchProfile process(SearchProfileCsvRow row) {
    if (failOnRow != null && row.rowNumber() == failOnRow) {
      throw new InjectedImportFailureException(row.rowNumber());
    }

    String profileId = required(row.profileId(), "profile_id is required");
    String source = required(row.source(), "source is required").toUpperCase(Locale.ROOT);
    if (!SUPPORTED_SOURCES.contains(source)) {
      throw new SearchProfileValidationException("Unsupported source: " + source);
    }

    String keywords = required(row.keywords(), "keywords are required");
    String employmentType =
        required(row.employmentType(), "employment_type is required").toUpperCase(Locale.ROOT);
    if (!SUPPORTED_EMPLOYMENT_TYPES.contains(employmentType)) {
      throw new SearchProfileValidationException("Unsupported employment_type: " + employmentType);
    }

    String activeValue = required(row.active(), "active is required").toLowerCase(Locale.ROOT);
    if (!activeValue.equals("true") && !activeValue.equals("false")) {
      throw new SearchProfileValidationException("active must be true or false: " + row.active());
    }

    return new SearchProfile(
        profileId,
        source,
        optional(row.sourceKey()),
        collapseWhitespace(keywords),
        optionalCollapsed(row.location()),
        normalizeSkills(row.includeSkills()),
        normalizeSkills(row.excludeSkills()),
        employmentType,
        Boolean.parseBoolean(activeValue));
  }

  private static String required(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new SearchProfileValidationException(message);
    }
    return value.trim();
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String optionalCollapsed(String value) {
    String normalized = optional(value);
    return normalized == null ? null : collapseWhitespace(normalized);
  }

  private static String collapseWhitespace(String value) {
    return value.trim().replaceAll("\\s+", " ");
  }

  static String normalizeSkills(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return Arrays.stream(value.split("\\|", -1))
        .map(SearchProfileProcessor::collapseWhitespace)
        .map(skill -> skill.toLowerCase(Locale.ROOT))
        .filter(skill -> !skill.isBlank())
        .distinct()
        .collect(Collectors.joining("|"));
  }
}
