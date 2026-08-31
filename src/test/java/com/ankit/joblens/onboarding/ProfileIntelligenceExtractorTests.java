package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileIntelligenceExtractorTests {
  private final ProfileIntelligenceExtractor extractor = new ProfileIntelligenceExtractor();

  @Test
  void extractsFrontendAliasesWithEvidenceAndRanksRecentExperienceTitles() {
    var result =
        extractor.extract(
            """
            Priya Shah
            priya@example.com
            Professional Summary
            Builds accessible interfaces with React.js and TypeScript.
            Experience
            Senior Front End Developer — Example Retail, 2023 - Present
            Delivered HTML and CSS design systems.
            Education
            Bachelor of Design
            """,
            List.of(
                new ProfileIntelligenceExtractor.SkillDefinition(
                    1, "React", "FRONTEND", List.of("React", "React.js")),
                new ProfileIntelligenceExtractor.SkillDefinition(
                    2, "TypeScript", "FRONTEND", List.of("TypeScript", "TS"))),
            List.of(
                new ProfileIntelligenceExtractor.RoleDefinition(
                    3,
                    "Frontend Engineer",
                    "FRONTEND",
                    List.of("Frontend Engineer", "Front End Developer"))));

    assertThat(result.skills())
        .extracting(
            ProfileIntelligenceExtractor.DetectedSkill::name,
            ProfileIntelligenceExtractor.DetectedSkill::matchedTerm)
        .containsExactly(tuple("React", "React.js"), tuple("TypeScript", "TypeScript"));
    assertThat(result.skills().getFirst().evidence()).contains("React.js");
    assertThat(result.roles())
        .singleElement()
        .satisfies(
            role -> {
              assertThat(role.name()).isEqualTo("Frontend Engineer");
              assertThat(role.evidenceSource()).isEqualTo("RECENT_EXPERIENCE");
              assertThat(role.evidence()).contains("Front End Developer");
              assertThat(role.confidence()).isEqualByComparingTo("0.850");
            });
  }

  @Test
  void extractsANonItHeadlineWithoutInventingUncataloguedTitles() {
    var result =
        extractor.extract(
            """
            Maria Santos
            Registered Nurse
            maria@example.com
            Professional Summary
            Acute care practitioner.
            Experience
            Registered Nurse — Community Hospital
            Education
            Bachelor of Nursing
            """,
            List.of(
                new ProfileIntelligenceExtractor.SkillDefinition(
                    1, "Nursing", "HEALTHCARE", List.of("Nursing", "Registered Nurse"))),
            List.of(
                new ProfileIntelligenceExtractor.RoleDefinition(
                    2, "Registered Nurse", "HEALTHCARE", List.of("Registered Nurse")),
                new ProfileIntelligenceExtractor.RoleDefinition(
                    3, "Accountant", "FINANCE", List.of("Accountant"))));

    assertThat(result.skills())
        .extracting(ProfileIntelligenceExtractor.DetectedSkill::name)
        .containsExactly("Nursing");
    assertThat(result.roles())
        .singleElement()
        .satisfies(
            role -> {
              assertThat(role.name()).isEqualTo("Registered Nurse");
              assertThat(role.evidenceSource()).isEqualTo("RESUME_HEADLINE");
              assertThat(role.confidence()).isEqualByComparingTo("0.950");
            });
  }

  private static org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.groups.Tuple.tuple(values);
  }
}
