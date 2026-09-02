package com.ankit.joblens.dashboard;

import com.ankit.joblens.onboarding.SearchKeywordNormalizer;
import com.ankit.joblens.onboarding.SearchPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PortalSearchQueryPlanner {

  public List<PortalSearchQuery> plan(SearchPreferences preferences, List<String> candidateSkills) {
    List<String> roles =
        csv(preferences.targetRoles()).stream().map(SearchKeywordNormalizer::normalize).toList();
    if (roles.isEmpty()) {
      roles = List.of(SearchKeywordNormalizer.normalize(preferences.keywords()));
    }
    List<String> sectors = csv(preferences.targetDomains());
    List<String> technologies = technologies(preferences.keywords(), candidateSkills);

    String primaryRole = roles.getFirst();
    String alternateRole = roles.size() > 1 ? roles.get(1) : primaryRole;
    List<String> selectedRoles = roles.stream().limit(2).toList();
    List<String> selectedSectors = sectors.stream().limit(2).toList();
    List<String> selectedTechnologies = technologies.stream().limit(2).toList();

    return List.of(
        new PortalSearchQuery(
            "Primary role + sectors",
            appendGroup(quote(primaryRole), selectedSectors),
            natural(primaryRole, selectedSectors)),
        new PortalSearchQuery(
            "Alternate role + technology + sector",
            appendGroup(
                appendGroup(booleanGroup(selectedRoles), selectedTechnologies), selectedSectors),
            natural(
                alternateRole,
                concat(selectedTechnologies, selectedSectors.stream().limit(1).toList()))),
        new PortalSearchQuery(
            "Broad fallback",
            broadRoleQuery(primaryRole, alternateRole),
            preferences.keywords().isBlank()
                ? primaryRole
                : SearchKeywordNormalizer.normalize(preferences.keywords())));
  }

  private static List<String> technologies(String keywords, List<String> candidateSkills) {
    String normalizedKeywords = normalize(keywords);
    List<String> matches =
        candidateSkills.stream()
            .filter(skill -> normalizedKeywords.contains(normalize(skill)))
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    var selected = new LinkedHashSet<String>();
    for (String skill : matches) {
      boolean covered =
          selected.stream()
              .map(PortalSearchQueryPlanner::normalize)
              .anyMatch(existing -> existing.contains(normalize(skill)));
      if (!covered) {
        selected.add(skill);
      }
    }
    if (selected.isEmpty()) {
      candidateSkills.stream().limit(2).forEach(selected::add);
    }
    if (selected.isEmpty()) {
      selected.add(SearchKeywordNormalizer.normalize(keywords));
    }
    return List.copyOf(selected);
  }

  private static List<String> csv(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .distinct()
        .toList();
  }

  private static String booleanGroup(List<String> values) {
    return values.stream()
        .map(PortalSearchQueryPlanner::quote)
        .collect(java.util.stream.Collectors.joining(" OR ", "(", ")"));
  }

  private static String broadRoleQuery(String primaryRole, String alternateRole) {
    if (primaryRole.equals(alternateRole)) {
      return quote(primaryRole);
    }
    return quote(primaryRole) + " OR " + quote(alternateRole);
  }

  private static String appendGroup(String query, List<String> values) {
    return values.isEmpty() ? query : query + " AND " + booleanGroup(values);
  }

  private static String quote(String value) {
    return "\"" + value.replace("\"", "").trim() + "\"";
  }

  private static String natural(String first, List<String> remaining) {
    return String.join(" ", concat(List.of(first), remaining));
  }

  private static List<String> concat(List<String> first, List<String> second) {
    var combined = new ArrayList<String>(first);
    combined.addAll(second);
    return combined;
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
  }
}
