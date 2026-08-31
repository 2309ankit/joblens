package com.ankit.joblens.onboarding;

import java.math.BigDecimal;
import java.util.List;

public record ProfileIntelligence(
    List<SkillSuggestion> skillSuggestions, List<RoleSuggestion> roleSuggestions) {

  public record SkillSuggestion(
      String name, String category, String matchedTerm, String evidence, BigDecimal confidence) {}

  public record RoleSuggestion(
      String name,
      String category,
      String evidenceSource,
      String evidence,
      BigDecimal confidence) {}
}
