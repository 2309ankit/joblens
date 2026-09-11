package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JobScoreCalculatorTests {
  @Test
  void matchesACandidateSpecificSkillDirectlyWithoutAddingItToGlobalJobSkills() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CandidateProfileService profiles = mock(CandidateProfileService.class);
    var skills = new LinkedHashMap<Long, CandidateProfileConfig.CandidateSkill>();
    skills.put(
        501L,
        new CandidateProfileConfig.CandidateSkill("Clinical Documentation", "PRODUCTION", 1.0));
    when(profiles.load(99L))
        .thenReturn(
            new CandidateProfileConfig(
                99L,
                "Singapore",
                List.of(),
                Set.of("Registered Nurse"),
                Set.of("healthcare"),
                skills,
                Map.of()));
    var calculator = new JobScoreCalculator(jdbc, profiles);
    var job =
        new NormalizedJobView(
            1L,
            "ADZUNA",
            "custom-skill-job",
            "Registered Nurse",
            "Community Hospital",
            "Singapore",
            "Maintain accurate clinical documentation for every patient.",
            "PERMANENT",
            null,
            null,
            null,
            "ONSITE",
            OffsetDateTime.now(),
            "https://example.test/job",
            "a".repeat(64));

    JobScore score = calculator.calculate(job, 99L);

    assertThat(score.technical()).isEqualTo(33);
    assertThat(score.bestRole().targetRoleName()).isEqualTo("Registered Nurse");
    assertThat(score.bestRole().calibrationPackCode()).isNull();
    assertThat(score.qualifiesRecommended()).isTrue();
    assertThat(score.reasons())
        .filteredOn(reason -> reason.category().equals("CONFIRMED_SKILLS"))
        .singleElement()
        .satisfies(reason -> assertThat(reason.text()).contains("Clinical Documentation"));
  }

  @Test
  void doesNotQualifyABroadTargetRolePhraseMatchedOnlyByAFillerWord() {
    var role = new RoleRankingContext.TargetRole(null, "Software Engineer", 1, List.of(), null);

    boolean qualifies =
        JobScoreCalculator.titleQualifiesForRecommendation(".NET Software Engineer", role);

    assertThat(qualifies).isFalse();
  }

  @Test
  void qualifiesADistinctiveUncalibratedRoleNameThatIsFullyMatched() {
    var role = new RoleRankingContext.TargetRole(null, "DevOps Engineer", 1, List.of(), null);

    boolean qualifies =
        JobScoreCalculator.titleQualifiesForRecommendation("Senior DevOps Engineer", role);

    assertThat(qualifies).isTrue();
  }

  @Test
  void qualifiesACuratedAliasMatchEvenWhenTheAliasPhraseIsBroad() {
    var role =
        new RoleRankingContext.TargetRole(
            null, "Some Obscure Role Name", 1, List.of("Software Engineer"), null);

    boolean qualifies =
        JobScoreCalculator.titleQualifiesForRecommendation(".NET Software Engineer", role);

    assertThat(qualifies).isTrue();
  }

  @Test
  void qualifiesACalibrationPackTitleSignalMatchEvenWhenTheSignalPhraseIsBroad() {
    var pack =
        new RoleRankingContext.CalibrationPack(
            1L,
            "TEST",
            "1.0.0",
            "Test pack",
            List.of(new RoleRankingContext.TitleSignal("Software Engineer", "PRIMARY")),
            List.of());
    var role =
        new RoleRankingContext.TargetRole(null, "Some Obscure Role Name", 1, List.of(), pack);

    boolean qualifies =
        JobScoreCalculator.titleQualifiesForRecommendation(".NET Software Engineer", role);

    assertThat(qualifies).isTrue();
  }

  @Test
  void doesNotQualifyWhenNoDistinctiveTitleTokenOverlaps() {
    var role = new RoleRankingContext.TargetRole(null, "Frontend Engineer", 1, List.of(), null);

    boolean qualifies = JobScoreCalculator.titleQualifiesForRecommendation(".NET Engineer", role);

    assertThat(qualifies).isFalse();
  }

  @Test
  void qualifiesRecommendedWhenSkillEvidenceAloneIsPositive() {
    assertThat(JobScoreCalculator.qualifiesRecommended(1, false)).isTrue();
  }

  @Test
  void doesNotQualifyRecommendedWithNoSkillAndNoTitleEvidence() {
    assertThat(JobScoreCalculator.qualifiesRecommended(0, false)).isFalse();
  }
}
