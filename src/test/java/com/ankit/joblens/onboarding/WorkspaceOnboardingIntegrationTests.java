package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.workspace.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class WorkspaceOnboardingIntegrationTests {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("joblens_workspace_test")
          .withUsername("joblens")
          .withPassword("joblens-test");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private WorkspaceRepository workspaces;
  @Autowired private OnboardingRepository onboarding;
  @Autowired private OnboardingService onboardingService;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void keepsAnonymousWorkspacesIndependentAndActivatesConfirmedProfiles() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    long firstCandidate = createAndConfirm(first, "Java Developer", "banking", "Java");
    long secondCandidate = createAndConfirm(second, "Platform Engineer", "technology", "AWS");

    assertThat(firstCandidate).isNotEqualTo(secondCandidate);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM workspace_profile_version WHERE status='ACTIVE' AND workspace_id IN (?, ?)",
                Integer.class,
                first,
                second))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM candidate_skill WHERE candidate_profile_id=?",
                Integer.class,
                firstCandidate))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM candidate_skill WHERE candidate_profile_id=?",
                Integer.class,
                secondCandidate))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM search_profile WHERE workspace_id IN (?, ?) AND active=true",
                Integer.class,
                first,
                second))
        .isEqualTo(2);

    onboardingService.savePreferences(
        first,
        new SearchPreferences(
            "Java Developer",
            "banking",
            "Singapore",
            "Java Spring",
            "Singapore",
            "sg",
            List.of("ADZUNA", "GREENHOUSE"),
            "examplebank",
            4,
            "PERMANENT",
            "HYBRID"));
    long revisedCandidate = onboardingService.confirm(first);

    assertThat(revisedCandidate).isEqualTo(firstCandidate);
    assertThat(
            jdbc.queryForList(
                "SELECT source FROM search_profile WHERE workspace_id=? AND active=true ORDER BY source",
                String.class,
                first))
        .containsExactly("ADZUNA", "GREENHOUSE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM workspace_profile_version WHERE workspace_id=? AND status='SUPERSEDED'",
                Integer.class,
                first))
        .isEqualTo(1);
  }

  @Test
  void reviewsSkillsInANewDraftBeforeChangingTheActiveCandidate() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    long firstCandidate = createAndConfirm(first, "Java Developer", "banking", "Java");
    long secondCandidate = createAndConfirm(second, "Platform Engineer", "technology", "AWS");

    OnboardingProfile draft =
        onboardingService.updateSkills(first, List.of("Spring Boot", "Kafka"));

    assertThat(draft.status()).isEqualTo("DRAFT");
    assertThat(draft.version()).isEqualTo(2);
    assertThat(draft.skills()).containsExactly("Kafka", "Spring Boot");
    assertThat(candidateSkills(firstCandidate)).containsExactly("Java");
    assertThat(candidateSkills(secondCandidate)).containsExactly("AWS");

    assertThatThrownBy(() -> onboardingService.updateSkills(first, List.of("Imaginary Skill")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Choose skills from the supported catalog");
    assertThat(onboardingService.latest(first).orElseThrow().skills())
        .containsExactly("Kafka", "Spring Boot");

    long confirmedCandidate = onboardingService.confirm(first);

    assertThat(confirmedCandidate).isEqualTo(firstCandidate);
    assertThat(candidateSkills(firstCandidate)).containsExactly("Kafka", "Spring Boot");
    assertThat(candidateSkills(secondCandidate)).containsExactly("AWS");
  }

  private List<String> candidateSkills(long candidateProfileId) {
    return jdbc.queryForList(
        """
        SELECT skill.canonical_name
        FROM candidate_skill
        JOIN skill ON skill.id = candidate_skill.skill_id
        WHERE candidate_skill.candidate_profile_id = ?
        ORDER BY skill.canonical_name
        """,
        String.class,
        candidateProfileId);
  }

  private long createAndConfirm(UUID workspaceId, String role, String domain, String skill) {
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(
            workspaceId,
            "resume.pdf",
            "application/pdf",
            100,
            "a".repeat(63) + (skill.equals("Java") ? "1" : "2"));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Candidate");
    onboarding.addSkills(profileId, List.of(skill));
    onboarding.savePreferences(
        workspaceId,
        profileId,
        new SearchPreferences(
            role,
            domain,
            "Singapore",
            skill,
            "Singapore",
            "sg",
            List.of("ADZUNA"),
            "",
            2,
            "PERMANENT",
            "HYBRID"));
    return onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());
  }
}
