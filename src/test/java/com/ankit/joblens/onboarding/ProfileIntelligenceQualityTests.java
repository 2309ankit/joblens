package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileIntelligenceQualityTests {
  private final ProfileIntelligenceExtractor extractor = new ProfileIntelligenceExtractor();

  @Test
  void meetsTheInitialExplicitTaxonomyKeywordQualityGateAcrossRedactedProfessions() {
    List<ProfileIntelligenceExtractor.SkillDefinition> skills =
        List.of(
            skill(1, "Python", "Python"),
            skill(2, "Machine Learning", "Machine Learning", "ML"),
            skill(3, "TypeScript", "TypeScript", "TS"),
            skill(4, "React", "React", "React.js"),
            skill(5, "Nursing", "Nursing"),
            skill(6, "Patient Care", "Patient Care"),
            skill(7, "Clinical Documentation", "Clinical Documentation"),
            skill(8, "CRM", "CRM"),
            skill(9, "Account Management", "Account Management"),
            skill(10, "Stakeholder Management", "Stakeholder Management"),
            skill(11, "SQL", "SQL"),
            skill(12, "Data Analysis", "Data Analysis"),
            skill(13, "Power BI", "Power BI", "PowerBI"),
            skill(14, "Microsoft Excel", "Microsoft Excel", "Excel"),
            skill(15, "Medium", "medium"),
            skill(16, "R", "R"));
    List<ProfileIntelligenceExtractor.RoleDefinition> roles =
        List.of(
            role(101, "AI Engineer", "AI Engineer"),
            role(102, "Frontend Engineer", "Front-end Developer"),
            role(103, "Registered Nurse", "Registered Nurse", "RN"),
            role(104, "Account Executive", "Account Executive"),
            role(105, "Data Analyst", "Data Analyst"),
            role(106, "Medium", "medium"));
    List<Fixture> fixtures =
        List.of(
            new Fixture(
                """
                Candidate One
                Senior AI Engineer
                Blog: medium.com/@candidate
                Professional Summary
                Builds production AI products and web applications.
                Experience
                AI Engineer — Example Systems, 2023 - Present
                Front-end Developer — Example Retail, 2020 - 2023
                Skills
                Python, Machine Learning, TypeScript, React.js
                """,
                Set.of("Python", "Machine Learning", "TypeScript", "React"),
                Set.of("AI Engineer", "Frontend Engineer")),
            new Fixture(
                """
                Candidate Two
                Registered Nurse
                Professional Summary
                Acute-care practitioner supporting safe discharge.
                Experience
                Registered Nurse — Community Hospital, 2021 - Present
                Skills
                Nursing, Patient Care, Clinical Documentation
                """,
                Set.of("Nursing", "Patient Care", "Clinical Documentation"),
                Set.of("Registered Nurse")),
            new Fixture(
                """
                Candidate Three
                Account Executive
                Professional Summary
                Enterprise growth professional.
                Experience
                Account Executive — Example Company, 2022 - Present
                Core Competencies
                CRM, Account Management, Stakeholder Management
                """,
                Set.of("CRM", "Account Management", "Stakeholder Management"),
                Set.of("Account Executive")),
            new Fixture(
                """
                Candidate Four
                Data Analyst
                Professional Summary
                Turns operational data into reviewed decisions.
                Experience
                Data Analyst — Example Service, 2020 - Present
                Tools
                SQL, Data Analysis, PowerBI, Excel
                """,
                Set.of("SQL", "Data Analysis", "Power BI", "Microsoft Excel"),
                Set.of("Data Analyst")));

    int expectedSkillCount = 0;
    int detectedSkillCount = 0;
    int correctSkillCount = 0;
    for (Fixture fixture : fixtures) {
      ProfileIntelligenceExtractor.Extraction extraction =
          extractor.extract(fixture.text(), skills, roles);
      Set<String> detectedSkills = names(extraction.skills());
      Set<String> detectedRoles = roleNames(extraction.roles());
      expectedSkillCount += fixture.expectedSkills().size();
      detectedSkillCount += detectedSkills.size();
      correctSkillCount +=
          detectedSkills.stream().filter(fixture.expectedSkills()::contains).count();
      assertThat(detectedRoles).containsExactlyInAnyOrderElementsOf(fixture.expectedRoles());
      assertThat(detectedRoles).doesNotContain("Medium");
    }

    double recall = (double) correctSkillCount / expectedSkillCount;
    double precision = (double) correctSkillCount / detectedSkillCount;
    assertThat(recall).isGreaterThanOrEqualTo(0.90);
    assertThat(precision).isGreaterThanOrEqualTo(0.95);
  }

  private static ProfileIntelligenceExtractor.SkillDefinition skill(
      long id, String name, String... terms) {
    return new ProfileIntelligenceExtractor.SkillDefinition(
        id, name, "TEST", List.of(terms), "reviewed-fixture-v1");
  }

  private static ProfileIntelligenceExtractor.RoleDefinition role(
      long id, String name, String... terms) {
    return new ProfileIntelligenceExtractor.RoleDefinition(
        id, name, "TEST", List.of(terms), "reviewed-fixture-v1");
  }

  private static Set<String> names(List<ProfileIntelligenceExtractor.DetectedSkill> detected) {
    var names = new LinkedHashSet<String>();
    detected.forEach(skill -> names.add(skill.name()));
    return names;
  }

  private static Set<String> roleNames(List<ProfileIntelligenceExtractor.DetectedRole> detected) {
    var names = new LinkedHashSet<String>();
    detected.forEach(role -> names.add(role.name()));
    return names;
  }

  private record Fixture(String text, Set<String> expectedSkills, Set<String> expectedRoles) {}
}
