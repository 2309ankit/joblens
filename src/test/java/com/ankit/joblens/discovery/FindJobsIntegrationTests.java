package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.onboarding.OnboardingRepository;
import com.ankit.joblens.onboarding.SearchPreferences;
import com.ankit.joblens.workspace.WorkspaceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class FindJobsIntegrationTests {
  private static final MockWebServer ADZUNA = new MockWebServer();

  static {
    try {
      ADZUNA.start();
    } catch (java.io.IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("joblens_find_jobs_test")
          .withUsername("joblens")
          .withPassword("joblens-test");

  @AfterAll
  static void stopServer() throws Exception {
    ADZUNA.shutdown();
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
  }

  @Autowired private WorkspaceRepository workspaces;
  @Autowired private OnboardingRepository onboarding;
  @Autowired private FindJobsService findJobs;
  @Autowired private WorkspaceSearchRunRepository runs;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JobOperator jobOperator;

  @Autowired
  @Qualifier("findJobsJob")
  private Job findJobsJob;

  @Test
  void oneClickPipelinePersistsWorkspaceJobsAndCandidateScoresIdempotently() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "1");
    ADZUNA.enqueue(response("ONE-1"));

    JobExecution execution =
        findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 1));

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepNames(execution))
        .containsExactly(
            "jobDiscoveryStep",
            "jobNormalizationStep",
            "skillExtractionStep",
            "exactDuplicateDetectionStep",
            "fuzzyDuplicateDetectionStep",
            "scoringStep");
    assertThat(countSightings(workspaceId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM job_score score
                JOIN normalized_job job ON job.id=score.normalized_job_id
                JOIN workspace_job_sighting sighting ON sighting.raw_job_posting_id=job.raw_job_posting_id
                WHERE sighting.workspace_id=? AND score.candidate_profile_id=?
                """,
                Integer.class,
                workspaceId,
                candidateProfileId))
        .isEqualTo(1);
    assertThat(findJobs.runs(workspaceId))
        .singleElement()
        .satisfies(run -> assertThat(run).containsEntry("status", "COMPLETED"));

    assertThatThrownBy(
            () -> findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 1)))
        .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    assertThat(countSightings(workspaceId)).isEqualTo(1);
  }

  @Test
  void restartsFailedParentJobWithoutRepeatingCompletedDiscovery() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "2");
    ADZUNA.enqueue(response("RESTART-1"));
    var parameters =
        new JobParametersBuilder()
            .addLocalDate("businessDate", LocalDate.of(2060, 1, 2), true)
            .addString("workspaceId", workspaceId.toString(), true)
            .addLong("candidateProfileId", candidateProfileId, true)
            .addString("searchDefinitionVersion", runs.definitionVersion(workspaceId), true)
            .addString("normalizationVersion", "v1", true)
            .addString("duplicateDetectionVersion", "fuzzy-v1", true)
            .addLong("failAfterItems", 1L, false)
            .toJobParameters();

    int requestsBefore = ADZUNA.getRequestCount();
    JobExecution failed = jobOperator.start(findJobsJob, parameters);
    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(countSightings(workspaceId)).isEqualTo(1);

    JobExecution restarted = jobOperator.restart(failed);

    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
    assertThat(ADZUNA.getRequestCount()).isEqualTo(requestsBefore + 1);
    assertThat(countSightings(workspaceId)).isEqualTo(1);
  }

  private long confirm(UUID workspaceId, String suffix) {
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(
            workspaceId, "resume.pdf", "application/pdf", 100, "a".repeat(63) + suffix);
    long profileVersionId = onboarding.createDraft(workspaceId, resumeId, "Java Candidate");
    onboarding.addSkills(profileVersionId, List.of("Java", "Spring Boot"));
    onboarding.savePreferences(
        workspaceId,
        profileVersionId,
        new SearchPreferences(
            "Java Developer",
            "banking",
            "Singapore",
            "Java Spring",
            "Singapore",
            "sg",
            List.of("ADZUNA"),
            "",
            2,
            "PERMANENT",
            "HYBRID"));
    return onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());
  }

  private int countSightings(UUID workspaceId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM workspace_job_sighting WHERE workspace_id=?",
        Integer.class,
        workspaceId);
  }

  private static List<String> stepNames(JobExecution execution) {
    return execution.getStepExecutions().stream().map(step -> step.getStepName()).toList();
  }

  private static MockResponse response(String id) {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {"count":1,"results":[{
              "id":"%s","title":"Senior Java Engineer",
              "company":{"display_name":"Example Bank"},
              "location":{"display_name":"Singapore"},
              "description":"<p>Java Spring Boot Kafka payments</p>",
              "contract_type":"permanent","created":"2026-08-30T00:00:00Z",
              "redirect_url":"https://example.test/jobs/%s"
            }]}
            """
                .formatted(id, id));
  }
}
