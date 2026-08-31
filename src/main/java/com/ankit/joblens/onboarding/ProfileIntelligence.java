package com.ankit.joblens.onboarding;

import java.math.BigDecimal;
import java.util.List;

public record ProfileIntelligence(
    List<SkillSuggestion> skillSuggestions,
    List<RoleSuggestion> roleSuggestions,
    List<TermSuggestion> termSuggestions) {

  public ProfileIntelligence(
      List<SkillSuggestion> skillSuggestions, List<RoleSuggestion> roleSuggestions) {
    this(skillSuggestions, roleSuggestions, List.of());
  }

  public record SkillSuggestion(
      String name,
      String category,
      String matchedTerm,
      String evidence,
      BigDecimal confidence,
      String evidenceSection,
      String matchType,
      String extractorVersion,
      String taxonomyVersion) {
    public SkillSuggestion(
        String name, String category, String matchedTerm, String evidence, BigDecimal confidence) {
      this(
          name,
          category,
          matchedTerm,
          evidence,
          confidence,
          "RESUME_BODY",
          "EXACT_CANONICAL",
          "literal-v1",
          null);
    }
  }

  public record RoleSuggestion(
      String name,
      String category,
      String evidenceSource,
      String evidence,
      BigDecimal confidence,
      String matchType,
      String extractorVersion,
      String taxonomyVersion) {
    public RoleSuggestion(
        String name,
        String category,
        String evidenceSource,
        String evidence,
        BigDecimal confidence) {
      this(
          name,
          category,
          evidenceSource,
          evidence,
          confidence,
          "EXACT_CANONICAL",
          "literal-v1",
          null);
    }
  }

  public record TermSuggestion(
      String term,
      String kind,
      String evidenceSection,
      String evidence,
      BigDecimal evidenceStrength,
      String reviewState) {}
}
