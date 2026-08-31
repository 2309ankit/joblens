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

    assertThat(score.technical()).isEqualTo(40);
    assertThat(score.reasons())
        .filteredOn(reason -> reason.category().equals("TECHNICAL"))
        .singleElement()
        .satisfies(reason -> assertThat(reason.text()).contains("Clinical Documentation"));
  }
}
