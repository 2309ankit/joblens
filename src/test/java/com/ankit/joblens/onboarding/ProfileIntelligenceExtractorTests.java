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
              assertThat(role.confidence()).isEqualByComparingTo("0.900");
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

  @Test
  void extractsSalesCompetenciesAndPreservesUncataloguedTermsWithoutAcceptingNakedR() {
    var result =
        extractor.extract(
            """
            SAHIL
            Bengaluru, India
            PROFESSIONAL SUMMARY
            Customer Success & Account Management Professional with enterprise growth experience.
            CORE COMPETENCIES
            Growth & Lead Generation: Outbound Prospecting, Cold Outreach & Email Sequencing, Account Mining, Lead Qualification (BANT/MEDDPICC) Account Management & Farming: Relationship Management, Upselling, Cross-selling, Retention Strategy
            TOOLS & ECOSYSTEMS
            LinkedIn Navigator, ZoomInfo, Salesforce, Zoho CRM, MS Office Suite
            PROFESSIONAL EXPERIENCE
            Calsoft Senior Sales Executive | September 2025 – Present
            Tata Elxsi Account Executive | November 2022 – September 2025
            R
            EDUCATION
            B.Tech in Computer Science
            """,
            List.of(
                new ProfileIntelligenceExtractor.SkillDefinition(1, "CRM", "SALES", List.of("CRM")),
                new ProfileIntelligenceExtractor.SkillDefinition(
                    2, "Account Management", "SALES", List.of("Account Management")),
                new ProfileIntelligenceExtractor.SkillDefinition(
                    3, "Sales", "SALES", List.of("Sales")),
                new ProfileIntelligenceExtractor.SkillDefinition(4, "R", "DATA", List.of("R"))),
            List.of(
                new ProfileIntelligenceExtractor.RoleDefinition(
                    5, "Sales Executive", "SALES", List.of("Sales Executive")),
                new ProfileIntelligenceExtractor.RoleDefinition(
                    6, "Account Executive", "SALES", List.of("Account Executive"))));

    assertThat(result.skills())
        .extracting(ProfileIntelligenceExtractor.DetectedSkill::name)
        .containsExactly("Account Management", "CRM", "Sales");
    assertThat(result.roles())
        .extracting(ProfileIntelligenceExtractor.DetectedRole::name)
        .containsExactly("Account Executive", "Sales Executive");
    assertThat(result.terms())
        .extracting(ProfileIntelligenceExtractor.TermSuggestion::normalizedTerm)
        .contains("Outbound Prospecting", "Cold Outreach", "BANT", "MEDDPICC", "Salesforce");
    assertThat(result.terms())
        .anySatisfy(
            term -> {
              assertThat(term.normalizedTerm()).isEqualTo("R");
              assertThat(term.reviewState()).isEqualTo("REJECTED");
            });
  }

  private static org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.groups.Tuple.tuple(values);
  }
}
