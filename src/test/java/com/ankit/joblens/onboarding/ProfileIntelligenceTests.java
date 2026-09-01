package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileIntelligenceTests {

  @Test
  void groupsSkillSuggestionsByExistingCategoryInEncounterOrder() {
    var intelligence =
        new ProfileIntelligence(
            List.of(
                skill("CRM", "SALES", "0.990"),
                skill("Sales", "SALES", "0.900"),
                skill("Oracle", "DATA", "0.850")),
            List.of());

    assertThat(intelligence.skillGroups())
        .extracting(ProfileIntelligence.SkillGroup::category)
        .containsExactly("SALES", "DATA");
    assertThat(intelligence.skillGroups().getFirst().suggestions())
        .extracting(ProfileIntelligence.SkillSuggestion::name)
        .containsExactly("CRM", "Sales");
  }

  private static ProfileIntelligence.SkillSuggestion skill(
      String name, String category, String confidence) {
    return new ProfileIntelligence.SkillSuggestion(
        name, category, name, "evidence", new BigDecimal(confidence));
  }
}
