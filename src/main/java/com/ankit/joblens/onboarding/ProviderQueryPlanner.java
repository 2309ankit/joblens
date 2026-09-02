package com.ankit.joblens.onboarding;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProviderQueryPlanner {
  public static final String GENERATION_VERSION = "role-intent-v1";

  public List<GeneratedQuery> plan(
      List<TargetRole> targetRoles, List<CandidateSkill> candidateSkills, String override) {
    if (override != null && !override.isBlank()) {
      return List.of(
          new GeneratedQuery(
              null,
              null,
              SearchKeywordNormalizer.normalize(override),
              1,
              "OVERRIDE",
              GENERATION_VERSION));
    }
    return targetRoles.stream()
        .limit(3)
        .map(
            role ->
                new GeneratedQuery(
                    role.id(),
                    role.name(),
                    generatedText(role, candidateSkills),
                    role.priority(),
                    "GENERATED",
                    GENERATION_VERSION))
        .toList();
  }

  private static String generatedText(TargetRole role, List<CandidateSkill> candidateSkills) {
    var terms = new LinkedHashSet<String>();
    terms.add(SearchKeywordNormalizer.normalize(role.name()));
    candidateSkills.stream()
        .filter(skill -> relevant(role.category(), skill.category()))
        .map(CandidateSkill::name)
        .map(SearchKeywordNormalizer::normalize)
        .filter(term -> !term.isBlank())
        .limit(2)
        .forEach(terms::add);
    return String.join(" ", terms);
  }

  private static boolean relevant(String roleCategory, String skillCategory) {
    String role = normalizedCategory(roleCategory);
    String skill = normalizedCategory(skillCategory);
    if (role.equals(skill)) {
      return true;
    }
    return switch (role) {
      case "FRONTEND" -> Set.of("DESIGN", "SOFTWARE_ENGINEERING").contains(skill);
      case "DATA" -> Set.of("SOFTWARE_ENGINEERING", "ENGINEERING_TOOLS").contains(skill);
      case "SALES" -> Set.of("CUSTOMER_SUCCESS", "BUSINESS", "MARKETING").contains(skill);
      case "CUSTOMER_SUCCESS" -> Set.of("SALES", "BUSINESS").contains(skill);
      default -> false;
    };
  }

  private static String normalizedCategory(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public record TargetRole(long id, String name, String category, int priority) {}

  public record CandidateSkill(String name, String category) {}

  public record GeneratedQuery(
      Long roleId,
      String roleName,
      String text,
      int priority,
      String origin,
      String generationVersion) {}
}
