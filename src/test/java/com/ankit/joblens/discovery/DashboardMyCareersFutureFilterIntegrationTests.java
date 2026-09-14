package com.ankit.joblens.discovery;

import static com.ankit.joblens.jdbc.ClasspathSql.load;
import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.onboarding.OnboardingRepository;
import com.ankit.joblens.onboarding.SearchPreferences;
import com.ankit.joblens.workspace.WorkspaceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Isolated from {@link FindJobsIntegrationTests} deliberately: enabling real Jooble credentials
 * makes every SG-market workspace in the class auto-create a Jooble search profile, which would
 * break that file's many Jooble-unaware tests. This class owns its own Postgres/Adzuna/Jooble
 * servers instead.
 */
@SpringBootTest
@Testcontainers
class DashboardMyCareersFutureFilterIntegrationTests {
  private static final MockWebServer ADZUNA = new MockWebServer();
  private static final MockWebServer JOOBLE = new MockWebServer();

  static {
    try {
      ADZUNA.start();
      JOOBLE.start();
    } catch (java.io.IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("joblens_mcf_filter_test")
          .withUsername("joblens")
          .withPassword("joblens-test");

  @AfterAll
  static void stopServers() throws Exception {
    ADZUNA.shutdown();
    JOOBLE.shutdown();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("joblens.adzuna.base-url", () -> ADZUNA.url("/v1/api").toString());
    registry.add("joblens.adzuna.app-id", () -> "test-id");
    registry.add("joblens.adzuna.app-key", () -> "test-key");
    registry.add("joblens.adzuna.page-size", () -> "20");
    registry.add("joblens.adzuna.max-pages", () -> "2");
    registry.add("joblens.adzuna.retry-backoff", () -> "1ms");
    registry.add("joblens.jooble.countries[0].country-code", () -> "sg");
    registry.add("joblens.jooble.countries[0].base-url", () -> JOOBLE.url("/").toString());
    registry.add("joblens.jooble.countries[0].api-key", () -> "test-jooble-key");
    registry.add("joblens.jooble.timeout", () -> "10s");
    registry.add("joblens.jooble.page-size", () -> "20");
    registry.add("joblens.jooble.retry-attempts", () -> "1");
    registry.add("joblens.jooble.retry-backoff", () -> "1ms");
  }

  @Autowired private WorkspaceRepository workspaces;
  @Autowired private OnboardingRepository onboarding;
  @Autowired private FindJobsService findJobs;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private NamedParameterJdbcTemplate namedJdbc;

  @Test
  void dashboardHidesMyCareersFutureJobsOnlyWhileTheWorkspaceToggleIsOn() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "b".repeat(64));
    long profileVersionId = onboarding.createDraft(workspaceId, resumeId, "Java Candidate");
    onboarding.addSkills(profileVersionId, List.of("Java", "Spring Boot"));
    onboarding.savePreferences(
        workspaceId,
        profileVersionId,
        new SearchPreferences(
            "Java Developer",
            "banking",
            "Singapore",
            "Java Spring Boot",
            "SG | Singapore",
            2,
            "PERMANENT",
            "HYBRID"));
    long candidateProfileId =
        onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());

    ADZUNA.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"count":1,"results":[{
                  "id":"ADZ-1","title":"Senior Java Engineer",
                  "company":{"display_name":"Example Bank"},
                  "location":{"display_name":"Singapore"},
                  "description":"<p>Java Spring Boot Kafka</p>",
                  "contract_type":"permanent","created":"2026-08-30T00:00:00Z",
                  "redirect_url":"https://example.test/jobs/ADZ-1"
                }]}
                """));
    JOOBLE.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"totalCount":2,"jobs":[
                  {"id":"JB-MCF-1","title":"Java Engineer MyCareersFuture Listing",
                   "link":"https://sg.jooble.org/jdp/JB-MCF-1","source":"mycareersfuture.sg",
                   "company":"MCF Co","location":"Singapore","salary":"5000 - 7000 SGD",
                   "type":"Full-time","updated":"2026-08-30T00:00:00Z"},
                  {"id":"JB-OK-1","title":"Java Engineer Example Listing",
                   "link":"https://sg.jooble.org/jdp/JB-OK-1","source":"example.com",
                   "company":"Example Co","location":"Singapore","salary":"5000 - 7000 SGD",
                   "type":"Full-time","updated":"2026-08-30T00:00:00Z"}
                ]}
                """));

    // Ingest with the toggle off, so the MyCareersFuture job lands like an already-fetched
    // posting from before the owner ever turned the toggle on.
    findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 2, 1));

    MapSqlParameterSource visibleJobParameters =
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("candidateProfileId", candidateProfileId)
            .addValue("maxDaysOld", 30);
    assertThat(
            namedJdbc.queryForList(
                load("sql/dashboard/list-ranked-jobs.sql"), visibleJobParameters))
        .extracting(row -> row.get("title"))
        .containsExactlyInAnyOrder(
            "Senior Java Engineer",
            "Java Engineer MyCareersFuture Listing",
            "Java Engineer Example Listing");

    MapSqlParameterSource nvidiaTarget =
        new MapSqlParameterSource()
            .addValue("candidateProfileId", candidateProfileId)
            .addValue("title", "Java Engineer Example Listing");
    Map<String, Object> target =
        namedJdbc
            .queryForList(
                """
                SELECT job.id, job.normalized_content_hash
                FROM normalized_job job
                JOIN job_score score ON score.normalized_job_id = job.id
                WHERE score.candidate_profile_id = :candidateProfileId AND job.title = :title
                """,
                nvidiaTarget)
            .getFirst();
    jdbc.update(
        """
        INSERT INTO nvidia_job_score(
            normalized_job_id, candidate_profile_id, profile_version_id,
            normalized_content_hash, candidate_fingerprint, model_id, prompt_version, cache_key,
            total_score, confidence, qualifies_recommended, summary, reasons_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 99, 0.9, true, 'NVIDIA test score', CAST(? AS JSONB))
        """,
        target.get("id"),
        candidateProfileId,
        profileVersionId,
        target.get("normalized_content_hash"),
        "c".repeat(64),
        "nvidia/test-model",
        "test-prompt-v1",
        "d".repeat(64),
        "[{\"category\":\"skills\",\"explanation\":\"Strong match\"}]");

    Map<String, Object> nvidiaRanked =
        namedJdbc
            .queryForList(load("sql/dashboard/list-ranked-jobs.sql"), visibleJobParameters)
            .stream()
            .filter(row -> "Java Engineer Example Listing".equals(row.get("title")))
            .findFirst()
            .orElseThrow();
    assertThat(nvidiaRanked)
        .containsEntry("score", 99)
        .containsEntry("scoring_source", "NVIDIA_NEBIUS")
        .containsEntry("score_summary", "NVIDIA test score");

    jdbc.update(
        "UPDATE search_profile SET exclude_my_careers_future=true WHERE workspace_id=? AND source='JOOBLE'",
        workspaceId);

    assertThat(
            namedJdbc.queryForList(
                load("sql/dashboard/list-ranked-jobs.sql"), visibleJobParameters))
        .extracting(row -> row.get("title"))
        .containsExactlyInAnyOrder("Senior Java Engineer", "Java Engineer Example Listing");

    jdbc.update(
        "UPDATE search_profile SET exclude_my_careers_future=false WHERE workspace_id=? AND source='JOOBLE'",
        workspaceId);

    assertThat(
            namedJdbc.queryForList(
                load("sql/dashboard/list-ranked-jobs.sql"), visibleJobParameters))
        .extracting(row -> row.get("title"))
        .containsExactlyInAnyOrder(
            "Senior Java Engineer",
            "Java Engineer MyCareersFuture Listing",
            "Java Engineer Example Listing");
  }
}
