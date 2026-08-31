package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.discovery.JoobleProperties;
import com.ankit.joblens.workspace.WorkspaceRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
            "SG | Singapore",
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
        .containsExactly("ADZUNA");
    assertThat(
            jdbc.queryForObject(
                "SELECT greenhouse_boards = '{}' FROM workspace_search_definition WHERE workspace_id=?",
                Boolean.class,
                first))
        .isTrue();
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

  @Test
  void activatesJoobleAlongsideAdzunaOnlyWhenItsKeyIsConfigured() {
    UUID workspaceId = UUID.randomUUID();
    OnboardingRepository configuredOnboarding =
        new OnboardingRepository(
            new NamedParameterJdbcTemplate(jdbc),
            new JoobleProperties(
                "test-jooble-key",
                "https://sg.jooble.org",
                "sg",
                Duration.ofSeconds(10),
                20,
                1,
                Duration.ZERO));

    createAndConfirm(configuredOnboarding, workspaceId, "Java Developer", "banking", "Java");

    assertThat(
            jdbc.queryForList(
                "SELECT source FROM search_profile WHERE workspace_id=? AND active=true ORDER BY source",
                String.class,
                workspaceId))
        .containsExactly("ADZUNA", "JOOBLE");
  }

  @Test
  void persistsNormalizedMarketsAndCreatesOneRestartableSourceProfilePerMarket() {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "b".repeat(64));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Multi-market candidate");
    onboarding.addSkills(profileId, List.of("Java"));
    SearchPreferences preferences =
        new SearchPreferences(
            "Java Developer",
            "banking",
            "Singapore",
            "Java Spring",
            "SG | Singapore\nAU | Sydney\nNZ | Auckland",
            2,
            "PERMANENT",
            "HYBRID");
    onboarding.savePreferences(workspaceId, profileId, preferences);
    onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());

    assertThat(
            jdbc.queryForList(
                """
                SELECT target.country_code || '|' || target.location
                FROM workspace_search_target target
                JOIN workspace_search_definition definition
                  ON definition.id=target.search_definition_id
                WHERE definition.workspace_id=?
                ORDER BY target.priority
                """,
                String.class,
                workspaceId))
        .containsExactly("SG|Singapore", "AU|Sydney", "NZ|Auckland");
    assertThat(
            jdbc.queryForList(
                """
                SELECT source_key || '|' || location
                FROM search_profile
                WHERE workspace_id=? AND source='ADZUNA' AND active=true
                ORDER BY location
                """,
                String.class,
                workspaceId))
        .containsExactly("nz|Auckland", "sg|Singapore", "au|Sydney");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM search_profile WHERE workspace_id=? AND search_target_id IS NOT NULL",
                Integer.class,
                workspaceId))
        .isEqualTo(3);
    assertThat(onboarding.preferences(workspaceId).orElseThrow().targets())
        .containsExactly(
            new SearchTarget("SG", "Singapore"),
            new SearchTarget("AU", "Sydney"),
            new SearchTarget("NZ", "Auckland"));

    onboardingService.savePreferences(workspaceId, preferences);
    onboardingService.confirm(workspaceId);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM search_profile WHERE workspace_id=? AND active=true",
                Integer.class,
                workspaceId))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM workspace_search_target target JOIN workspace_search_definition definition ON definition.id=target.search_definition_id WHERE definition.workspace_id=?",
                Integer.class,
                workspaceId))
        .isEqualTo(3);
  }

  @Test
  void reviewsPreferencesAndSkillsInOneActivationTransaction() {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "c".repeat(64));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Backend engineer");
    onboarding.addSkills(profileId, List.of("Java"));

    long candidateId =
        onboardingService.completeSetup(
            workspaceId,
            List.of("Java", "Spring Boot"),
            new SearchPreferences(
                "Senior Backend Engineer",
                "banking",
                "Singapore",
                "Java Spring Boot",
                "SG | Singapore\nAU | Sydney",
                2,
                "PERMANENT",
                "HYBRID"));

    assertThat(onboarding.latestProfile(workspaceId).orElseThrow().status()).isEqualTo("ACTIVE");
    assertThat(candidateSkills(candidateId)).containsExactly("Java", "Spring Boot");
    assertThat(onboarding.preferences(workspaceId).orElseThrow().targets())
        .containsExactly(new SearchTarget("SG", "Singapore"), new SearchTarget("AU", "Sydney"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM search_profile WHERE workspace_id=? AND active=true",
                Integer.class,
                workspaceId))
        .isEqualTo(2);
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
    return createAndConfirm(onboarding, workspaceId, role, domain, skill);
  }

  private long createAndConfirm(
      OnboardingRepository repository, UUID workspaceId, String role, String domain, String skill) {
    workspaces.create(workspaceId);
    long resumeId =
        repository.saveResume(
            workspaceId,
            "resume.pdf",
            "application/pdf",
            100,
            "a".repeat(63) + (skill.equals("Java") ? "1" : "2"));
    long profileId = repository.createDraft(workspaceId, resumeId, "Candidate");
    repository.addSkills(profileId, List.of(skill));
    repository.savePreferences(
        workspaceId,
        profileId,
        new SearchPreferences(
            role, domain, "Singapore", skill, "SG | Singapore", 2, "PERMANENT", "HYBRID"));
    return repository.confirm(workspaceId, repository.latestProfile(workspaceId).orElseThrow());
  }
}
