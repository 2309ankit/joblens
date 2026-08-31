package com.ankit.joblens.discovery;

import static com.ankit.joblens.jdbc.ClasspathSql.load;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class FindJobsIntegrationTests {
  private static final MockWebServer ADZUNA = new MockWebServer();
  private static final MockWebServer GREENHOUSE = new MockWebServer();
  private static final MockWebServer LEVER = new MockWebServer();

  static {
    try {
      ADZUNA.start();
      GREENHOUSE.start();
      LEVER.start();
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
    GREENHOUSE.shutdown();
    LEVER.shutdown();
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
    registry.add("joblens.greenhouse.base-url", () -> GREENHOUSE.url("/").toString());
    registry.add("joblens.greenhouse.retry-attempts", () -> "1");
    registry.add("joblens.greenhouse.retry-backoff", () -> "1ms");
    registry.add("joblens.lever.base-url", () -> LEVER.url("/").toString());
    registry.add("joblens.lever.page-size", () -> "20");
    registry.add("joblens.lever.retry-attempts", () -> "1");
    registry.add("joblens.lever.retry-backoff", () -> "1ms");
  }

  @Autowired private WorkspaceRepository workspaces;
  @Autowired private OnboardingRepository onboarding;
  @Autowired private FindJobsService findJobs;
  @Autowired private WorkspaceSearchRunRepository runs;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private NamedParameterJdbcTemplate namedJdbc;
  @Autowired private JobOperator jobOperator;

  @Autowired
  @Qualifier("findJobsJob")
  private Job findJobsJob;

  @Test
  void oneClickPipelinePersistsWorkspaceJobsAndCandidateScoresIdempotently() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "1");
    ADZUNA.enqueue(response("ONE-1", "https://job-boards.greenhouse.io/examplebank/jobs/987"));
    GREENHOUSE.enqueue(greenhouseResponse());

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
    assertThat(countSightings(workspaceId)).isEqualTo(2);
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
        .isEqualTo(2);
    assertThat(
            jdbc.queryForMap(
                """
                SELECT board.source_key, board.status
                FROM workspace_source_board workspace_board
                JOIN discovered_source_board board ON board.id=workspace_board.discovered_source_board_id
                WHERE workspace_board.workspace_id=?
                """,
                workspaceId))
        .containsEntry("source_key", "examplebank")
        .containsEntry("status", "VALIDATED");
    assertThat(
            jdbc.queryForList(
                "SELECT source FROM search_profile WHERE workspace_id=? AND active=true ORDER BY source",
                String.class,
                workspaceId))
        .containsExactly("ADZUNA", "GREENHOUSE");
    assertThat(findJobs.runs(workspaceId))
        .singleElement()
        .satisfies(run -> assertThat(run).containsEntry("status", "COMPLETED"));
    FindJobsRunDetail detail = findJobs.detail(workspaceId, execution.getId()).orElseThrow();
    assertThat(detail.run().outcome()).isEqualTo("COMPLETED");
    assertThat(detail.sources())
        .extracting(SourceRunSummary::source, SourceRunSummary::status)
        .containsExactly(tuple("ADZUNA", "COMPLETED"), tuple("GREENHOUSE", "COMPLETED"));
    assertThat(detail.sources().getFirst().countryCode()).isEqualTo("SG");
    assertThat(detail.sources().getFirst().location()).isEqualTo("Singapore");
    assertThat(detail.sources().get(1).countryCode()).isNull();
    assertThat(detail.sources())
        .allSatisfy(
            source -> {
              assertThat(source.recordsReceived()).isEqualTo(1);
              assertThat(source.newRecords()).isEqualTo(1);
              assertThat(source.normalizedRecords()).isEqualTo(1);
              assertThat(source.scoredRecords()).isEqualTo(1);
            });
    UUID otherWorkspace = UUID.randomUUID();
    workspaces.create(otherWorkspace);
    assertThat(findJobs.detail(otherWorkspace, execution.getId())).isEmpty();

    assertThatThrownBy(
            () -> findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 1)))
        .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    assertThat(countSightings(workspaceId)).isEqualTo(2);

    ADZUNA.enqueue(
        response(
            "ONE-1",
            "https://job-boards.greenhouse.io/examplebank/jobs/987",
            "Principal Java Engineer"));
    GREENHOUSE.enqueue(greenhouseResponse());
    JobExecution changedExecution =
        findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 5));
    FindJobsRunDetail changed =
        findJobs.detail(workspaceId, changedExecution.getId()).orElseThrow();
    assertThat(changed.sources().get(0).changedRecords()).isEqualTo(1);
    assertThat(changed.sources().get(0).newRecords()).isZero();
    assertThat(changed.sources().get(1).unchangedRecords()).isEqualTo(1);
    assertThat(countSightings(workspaceId)).isEqualTo(2);

    MapSqlParameterSource visibleJobParameters =
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("candidateProfileId", candidateProfileId)
            .addValue("maxDaysOld", 30);
    assertThat(
            namedJdbc.queryForList(
                load("sql/dashboard/list-ranked-jobs.sql"), visibleJobParameters))
        .hasSize(2);

    jdbc.update(
        "UPDATE search_profile SET keywords='Sales Executive' WHERE workspace_id=? AND active=true",
        workspaceId);

    assertThat(
            namedJdbc.queryForList(
                load("sql/dashboard/list-ranked-jobs.sql"), visibleJobParameters))
        .isEmpty();
    assertThat(namedJdbc.queryForList(load("sql/job-query/list-jobs.sql"), visibleJobParameters))
        .isEmpty();
  }

  @Test
  void reportsPartialSourceFailureAndRestartsOnlyTheUnfinishedSource() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "3");
    ADZUNA.enqueue(response("PARTIAL-1", "https://job-boards.greenhouse.io/retrybank/jobs/987"));
    GREENHOUSE.enqueue(
        new MockResponse()
            .setResponseCode(503)
            .setBody("api_key=must-not-be-persisted provider unavailable"));

    int adzunaRequestsBefore = ADZUNA.getRequestCount();
    JobExecution failed = findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 3));

    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    FindJobsRunDetail partial = findJobs.detail(workspaceId, failed.getId()).orElseThrow();
    assertThat(partial.run().outcome()).isEqualTo("PARTIAL");
    assertThat(partial.sources())
        .extracting(SourceRunSummary::source, SourceRunSummary::status)
        .containsExactly(tuple("ADZUNA", "COMPLETED"), tuple("GREENHOUSE", "FAILED"));
    assertThat(partial.sources().get(1).failureReason())
        .contains("Transient Greenhouse HTTP status 503")
        .doesNotContain("must-not-be-persisted");
    assertThat(partial.sources().get(1).pagesAttempted()).isEqualTo(1);
    assertThat(partial.sources().get(1).pagesFetched()).isZero();
    assertThat(countSightings(workspaceId)).isEqualTo(1);

    GREENHOUSE.enqueue(greenhouseResponse("retrybank"));
    JobExecution restarted = findJobs.restart(workspaceId, failed.getId());

    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
    assertThat(ADZUNA.getRequestCount()).isEqualTo(adzunaRequestsBefore + 1);
    FindJobsRunDetail completed = findJobs.detail(workspaceId, restarted.getId()).orElseThrow();
    assertThat(completed.run().outcome()).isEqualTo("COMPLETED");
    assertThat(completed.sources())
        .extracting(SourceRunSummary::status)
        .containsExactly("COMPLETED", "COMPLETED");
    assertThat(countSightings(workspaceId)).isEqualTo(2);
  }

  @Test
  void reportsACompletedSourceWithNoResultsAsEmpty() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "4");
    ADZUNA.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"count\":0,\"results\":[]}"));

    JobExecution execution =
        findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 4));

    FindJobsRunDetail detail = findJobs.detail(workspaceId, execution.getId()).orElseThrow();
    assertThat(detail.run().outcome()).isEqualTo("COMPLETED");
    assertThat(detail.sources())
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.status()).isEqualTo("EMPTY");
              assertThat(source.pagesFetched()).isEqualTo(1);
              assertThat(source.recordsReceived()).isZero();
              assertThat(source.rawRecords()).isZero();
            });
  }

  @Test
  void discoversPublicLeverSiteAndRunsItThroughTheFullPipeline() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "5");
    ADZUNA.enqueue(response("LEVER-SEED", "https://jobs.lever.co/examplebank/lever-job-1"));
    LEVER.enqueue(leverResponse());

    JobExecution execution =
        findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 6));

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            jdbc.queryForMap(
                """
                SELECT board.source, board.source_key, board.status
                FROM workspace_source_board workspace_board
                JOIN discovered_source_board board
                  ON board.id=workspace_board.discovered_source_board_id
                WHERE workspace_board.workspace_id=?
                """,
                workspaceId))
        .containsEntry("source", "LEVER")
        .containsEntry("source_key", "examplebank")
        .containsEntry("status", "VALIDATED");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM normalized_job normalized
                JOIN raw_job_posting raw ON raw.id=normalized.raw_job_posting_id
                JOIN workspace_job_sighting sighting ON sighting.raw_job_posting_id=raw.id
                WHERE sighting.workspace_id=? AND normalized.source='LEVER'
                """,
                Integer.class,
                workspaceId))
        .isEqualTo(1);
    assertThat(findJobs.detail(workspaceId, execution.getId()).orElseThrow().sources())
        .extracting(SourceRunSummary::source, SourceRunSummary::status)
        .containsExactly(tuple("ADZUNA", "COMPLETED"), tuple("LEVER", "COMPLETED"));
  }

  @Test
  void restartsFailedLeverPageWithoutRepeatingCompletedBroadSource() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "6");
    ADZUNA.enqueue(response("LEVER-RESTART-SEED", "https://jobs.lever.co/retrybank/lever-job-1"));
    LEVER.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
    int adzunaRequestsBefore = ADZUNA.getRequestCount();

    JobExecution failed = findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 7));

    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(findJobs.detail(workspaceId, failed.getId()).orElseThrow().run().outcome())
        .isEqualTo("PARTIAL");

    LEVER.enqueue(leverResponse("retrybank"));
    JobExecution restarted = findJobs.restart(workspaceId, failed.getId());

    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
    assertThat(ADZUNA.getRequestCount()).isEqualTo(adzunaRequestsBefore + 1);
    assertThat(countSightings(workspaceId)).isEqualTo(2);
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

  @Test
  void runsEachNormalizedSearchMarketAsAnIndependentSourceProfile() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = confirm(workspaceId, "7", "SG | Singapore\nAU | Sydney");
    ADZUNA.enqueue(responseWithLocation("MARKET-ONE", "Singapore"));
    ADZUNA.enqueue(responseWithLocation("MARKET-TWO", "Sydney"));

    JobExecution execution =
        findJobs.run(workspaceId, candidateProfileId, LocalDate.of(2060, 1, 8));

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(findJobs.detail(workspaceId, execution.getId()).orElseThrow().sources())
        .extracting(SourceRunSummary::source, SourceRunSummary::status)
        .containsExactly(tuple("ADZUNA", "COMPLETED"), tuple("ADZUNA", "COMPLETED"));
    assertThat(
            jdbc.queryForList(
                "SELECT source_key FROM search_profile WHERE workspace_id=? AND active=true ORDER BY source_key",
                String.class,
                workspaceId))
        .containsExactly("au", "sg");
    assertThat(countSightings(workspaceId)).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                """
                SELECT reason_text FROM job_score_reason reason
                JOIN job_score score ON score.id=reason.job_score_id
                WHERE score.candidate_profile_id=? AND reason.category='LOCATION'
                ORDER BY reason_text
                """,
                String.class,
                candidateProfileId))
        .containsExactly(
            "Matched preferred market: Singapore, SG", "Matched preferred market: Sydney, AU");
  }

  private long confirm(UUID workspaceId, String suffix) {
    return confirm(workspaceId, suffix, "SG | Singapore");
  }

  private long confirm(UUID workspaceId, String suffix, String searchMarkets) {
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
            searchMarkets,
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
    return response(id, "https://example.test/jobs/" + id);
  }

  private static MockResponse responseWithLocation(String id, String location) {
    return response(id, "https://example.test/jobs/" + id, "Senior Java Engineer", location);
  }

  private static MockResponse response(String id, String sourceUrl) {
    return response(id, sourceUrl, "Senior Java Engineer");
  }

  private static MockResponse response(String id, String sourceUrl, String title) {
    return response(id, sourceUrl, title, "Singapore");
  }

  private static MockResponse response(String id, String sourceUrl, String title, String location) {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {"count":1,"results":[{
              "id":"%s","title":"%s",
              "company":{"display_name":"Example Bank"},
              "location":{"display_name":"%s"},
              "description":"<p>Java Spring Boot Kafka payments</p>",
              "contract_type":"permanent","created":"2026-08-30T00:00:00Z",
              "redirect_url":"%s"
            }]}
            """
                .formatted(id, title, location, sourceUrl));
  }

  private static MockResponse greenhouseResponse() {
    return greenhouseResponse("examplebank");
  }

  private static MockResponse greenhouseResponse(String board) {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {"jobs":[{
              "id":987,"title":"Senior Java Platform Engineer",
              "location":{"name":"Singapore"},
              "content":"<p>Java Spring Boot payments platform</p>",
              "updated_at":"2026-08-30T00:00:00Z",
              "absolute_url":"https://job-boards.greenhouse.io/%s/jobs/987"
            }]}
            """
                .formatted(board));
  }

  private static MockResponse leverResponse() {
    return leverResponse("examplebank");
  }

  private static MockResponse leverResponse(String site) {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            [{"id":"lever-job-1","text":"Senior Java Platform Engineer",
              "categories":{"location":"Singapore","commitment":"Full-time"},
              "description":"<p>Java Spring Boot payments platform</p>",
              "descriptionPlain":"Java Spring Boot payments platform",
              "hostedUrl":"https://jobs.lever.co/%s/lever-job-1",
              "applyUrl":"https://jobs.lever.co/%s/lever-job-1/apply",
              "workplaceType":"hybrid"}]
            """
                .formatted(site, site));
  }
}
