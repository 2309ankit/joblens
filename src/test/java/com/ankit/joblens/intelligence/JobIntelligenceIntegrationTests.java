package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.batchapi.DuplicateQueryController;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
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

@SpringBootTest(properties = "joblens.intelligence.chunk-size=2")
@Testcontainers
class JobIntelligenceIntegrationTests {

  private static final AtomicInteger DATE_SEQUENCE = new AtomicInteger();

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("joblens_intelligence_test")
          .withUsername("joblens")
          .withPassword("joblens-test");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private JobOperator jobOperator;

  @Autowired
  @Qualifier("jobIntelligenceJob")
  private Job intelligenceJob;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private DuplicateQueryController duplicateQueryController;

  private long sourceFetchRunId;
  private long discoveryExecutionId;

  @BeforeEach
  void cleanAndCreateRawInfrastructure() throws Exception {
    jdbcTemplate.update("DELETE FROM normalized_job");
    jdbcTemplate.update("DELETE FROM raw_job_posting");
    jdbcTemplate.update("DELETE FROM source_fetch_run");
    jdbcTemplate.update("DELETE FROM search_profile_rejection");
    jdbcTemplate.update("DELETE FROM search_profile");

    JobExecution infrastructureExecution = launch(null);
    jdbcTemplate.update(
        """
                INSERT INTO search_profile (
                    profile_id, source, source_key, keywords, location,
                    include_skills, exclude_skills, employment_type, active
                ) VALUES ('INT001', 'ADZUNA', 'sg', 'java', 'Singapore', '', '', 'ANY', true)
                """);
    sourceFetchRunId =
        jdbcTemplate.queryForObject(
            """
                INSERT INTO source_fetch_run (
                    source, search_profile_id, status, completed_at, pages_fetched,
                    records_received, next_page, job_instance_id, job_execution_id
                ) VALUES ('ADZUNA', 'INT001', 'COMPLETED', CURRENT_TIMESTAMP, 1, 0, 2, ?, ?)
                RETURNING id
                """,
            Long.class,
            infrastructureExecution.getJobInstanceId(),
            infrastructureExecution.getId());
    discoveryExecutionId = infrastructureExecution.getId();
  }

  @Test
  void normalizesValidAdzunaPostingAndPersistsAllFields() throws Exception {
    long rawId =
        insertRaw("A1", validJob("A1", "Senior Java Engineer", "<p>Java &amp; Spring</p>"));

    JobExecution execution = launch(null);

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(execution.getStepExecutions().iterator().next().getReadCount()).isEqualTo(1);
    assertThat(execution.getStepExecutions().iterator().next().getWriteCount()).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT title, company, location, description_text, employment_type,
                       salary_min, salary_max, salary_currency, remote_type,
                       posted_at, source_url, length(normalized_content_hash) AS hash_length
                FROM normalized_job WHERE raw_job_posting_id = ?
                """,
                rawId))
        .containsEntry("title", "Senior Java Engineer")
        .containsEntry("company", "Example Bank")
        .containsEntry("location", "Singapore")
        .containsEntry("description_text", "Java & Spring")
        .containsEntry("employment_type", "PERMANENT")
        .containsEntry("salary_currency", "SGD")
        .containsEntry("remote_type", "HYBRID")
        .containsEntry("hash_length", 64);
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT processing_status, processing_reason, processed_at IS NOT NULL AS processed
                FROM raw_job_posting WHERE id = ?
                """,
                rawId))
        .containsEntry("processing_status", "NORMALIZED")
        .containsEntry("processing_reason", null)
        .containsEntry("processed", true);
  }

  @Test
  void acceptsMissingOptionalFields() throws Exception {
    insertRaw("A2", "{\"id\":\"A2\",\"title\":\"Backend Engineer\"}");

    JobExecution execution = launch(null);

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT company, location, salary_min, salary_max, employment_type, remote_type
                FROM normalized_job WHERE external_job_id = 'A2'
                """))
        .containsEntry("company", null)
        .containsEntry("location", null)
        .containsEntry("salary_min", null)
        .containsEntry("salary_max", null)
        .containsEntry("employment_type", null)
        .containsEntry("remote_type", null);
  }

  @Test
  void rejectsMissingTitleAndNonObjectRawJsonWithReasons() throws Exception {
    insertRaw("BAD1", "{\"description\":\"missing title\"}");
    insertRaw("BAD2", "[]");

    JobExecution execution = launch(null);

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(execution.getStepExecutions().iterator().next().getProcessSkipCount()).isEqualTo(2);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM normalized_job", Integer.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT processing_status || ':' || processing_reason
                FROM raw_job_posting ORDER BY external_job_id
                """,
                String.class))
        .allMatch(value -> value.startsWith("REJECTED:"))
        .anyMatch(value -> value.contains("title"))
        .anyMatch(value -> value.contains("object"));
  }

  @Test
  void completedRowsAreNotReprocessedOnAnotherJobInstance() throws Exception {
    long rawId = insertRaw("A3", validJob("A3", "Java Engineer", "<p>Initial</p>"));
    JobExecution first = launch(null);
    var updatedAt =
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM normalized_job WHERE raw_job_posting_id = ?",
            java.sql.Timestamp.class,
            rawId);

    JobExecution second = launch(null);

    assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(second.getStepExecutions().iterator().next().getReadCount()).isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM normalized_job", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT updated_at FROM normalized_job WHERE raw_job_posting_id = ?",
                java.sql.Timestamp.class,
                rawId))
        .isEqualTo(updatedAt);
  }

  @Test
  void changedRawPayloadBecomesEligibleAndUpdatesNormalizedRow() throws Exception {
    long rawId = insertRaw("A4", validJob("A4", "Original Title", "Original"));
    launch(null);
    var createdAt =
        jdbcTemplate.queryForObject(
            "SELECT created_at FROM normalized_job WHERE raw_job_posting_id = ?",
            java.sql.Timestamp.class,
            rawId);
    var firstUpdatedAt =
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM normalized_job WHERE raw_job_posting_id = ?",
            java.sql.Timestamp.class,
            rawId);

    Thread.sleep(10);
    String changed = validJob("A4", "Changed Title", "Changed description");
    jdbcTemplate.update(
        """
                UPDATE raw_job_posting
                SET raw_payload_json = CAST(? AS jsonb), payload_hash = ?,
                    processing_status = 'NEW', processing_reason = NULL, processed_at = NULL
                WHERE id = ?
                """,
        changed,
        sha256(changed),
        rawId);

    JobExecution reprocessed = launch(null);

    assertThat(reprocessed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM normalized_job", Integer.class))
        .isEqualTo(1);
    var row =
        jdbcTemplate.queryForMap(
            """
                SELECT title, description_text, created_at, updated_at
                FROM normalized_job WHERE raw_job_posting_id = ?
                """,
            rawId);
    assertThat(row)
        .containsEntry("title", "Changed Title")
        .containsEntry("description_text", "Changed description")
        .containsEntry("created_at", createdAt);
    assertThat((java.sql.Timestamp) row.get("updated_at")).isAfter(firstUpdatedAt);
  }

  @Test
  void restartResumesAfterCommittedChunkWithoutDuplicates() throws Exception {
    long firstId = insertRaw("R1", validJob("R1", "One", "One"));
    long secondId = insertRaw("R2", validJob("R2", "Two", "Two"));
    long failedId = insertRaw("R3", validJob("R3", "Three", "Three"));
    insertRaw("R4", validJob("R4", "Four", "Four"));
    insertRaw("R5", validJob("R5", "Five", "Five"));

    JobExecution failed = launch(3L);

    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM normalized_job", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT processing_status FROM raw_job_posting WHERE id = ?",
                String.class,
                failedId))
        .isEqualTo("FAILED");
    assertThat(
            failed
                .getStepExecutions()
                .iterator()
                .next()
                .getExecutionContext()
                .getLong(RawJobPostingReader.LAST_COMMITTED_ID))
        .isEqualTo(secondId);

    JobExecution restarted = jobOperator.restart(failed);

    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
    assertThat(restarted.getId()).isNotEqualTo(failed.getId());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM normalized_job", Integer.class))
        .isEqualTo(5);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM (
                    SELECT raw_job_posting_id FROM normalized_job
                    GROUP BY raw_job_posting_id HAVING count(*) > 1
                ) duplicates
                """,
                Integer.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM raw_job_posting WHERE processing_status = 'NORMALIZED'
                """,
                Integer.class))
        .isEqualTo(5);
    assertThat(restarted.getStepExecutions().iterator().next().getReadCount()).isEqualTo(3);
    assertThat(firstId).isLessThan(secondId);
  }

  @Test
  void extractsSkillsAndPersistsExplainableScoreIdempotently() throws Exception {
    long rawId =
        insertRaw(
            "SCORE1",
            validJob(
                "SCORE1",
                "Senior Java Engineer",
                "Banking payments platform using SpringBoot, Kafka, K8s and Postgres"));

    JobExecution execution = launch(null);

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    long normalizedId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM normalized_job WHERE raw_job_posting_id = ?", Long.class, rawId);
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT s.canonical_name FROM job_skill js JOIN skill s ON s.id=js.skill_id
                WHERE js.normalized_job_id=? ORDER BY s.canonical_name
                """,
                String.class,
                normalizedId))
        .contains("Java", "Spring Boot", "Kafka", "Kubernetes", "PostgreSQL");
    var score =
        jdbcTemplate.queryForMap(
            """
                SELECT total_score, technical_score, domain_score, seniority_score,
                       location_score, employment_score, salary_score, freshness_score,
                       (SELECT sum(points) FROM job_score_reason r WHERE r.job_score_id=j.id) AS reason_sum
                FROM job_score j WHERE normalized_job_id=?
                """,
            normalizedId);
    assertThat(((Number) score.get("total_score")).intValue()).isBetween(0, 100);
    assertThat(((Number) score.get("reason_sum")).intValue())
        .isEqualTo(((Number) score.get("total_score")).intValue());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT ranking_policy_version FROM job_score WHERE normalized_job_id=?",
                String.class,
                normalizedId))
        .isEqualTo("universal-v2");
    int roleScoreCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM job_role_score WHERE normalized_job_id=?",
            Integer.class,
            normalizedId);
    assertThat(roleScoreCount).isPositive();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM job_role_score role_score
                WHERE role_score.normalized_job_id=?
                  AND role_score.total_score <> (
                    SELECT COALESCE(sum(reason.points), 0)
                    FROM job_role_score_reason reason
                    WHERE reason.job_role_score_id=role_score.id
                  )
                """,
                Integer.class,
                normalizedId))
        .isZero();

    launch(null);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_skill WHERE normalized_job_id=?",
                Integer.class,
                normalizedId))
        .isEqualTo(5);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_score WHERE normalized_job_id=?",
                Integer.class,
                normalizedId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_role_score WHERE normalized_job_id=?",
                Integer.class,
                normalizedId))
        .isEqualTo(roleScoreCount);
  }

  @Test
  void clustersExactNormalizedContentDuplicatesWithPersistedEvidenceAndRestInspection()
      throws Exception {
    insertRaw("DUP1", validJob("DUP1", "Senior Java Engineer", "Same normalized content"));
    insertRaw("DUP2", validJob("DUP2", "Senior Java Engineer", "Same normalized content"));
    insertRaw("UNIQUE", validJob("UNIQUE", "Different role", "Different normalized content"));

    JobExecution execution = launch(null);

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM duplicate_cluster", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT member_count FROM duplicate_cluster", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM duplicate_cluster_member", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM duplicate_cluster_member WHERE is_canonical
                """,
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT evidence_type FROM duplicate_match_evidence
                """,
                String.class))
        .containsExactly("NORMALIZED_CONTENT_HASH");

    var clusters = duplicateQueryController.clusters();
    assertThat(clusters).hasSize(1);
    long clusterId = ((Number) clusters.getFirst().get("id")).longValue();
    assertThat((java.util.List<?>) duplicateQueryController.cluster(clusterId).get("members"))
        .hasSize(2);
    assertThat((java.util.List<?>) duplicateQueryController.cluster(clusterId).get("evidence"))
        .hasSize(1);
  }

  @Test
  void exactDuplicateReconciliationIsIdempotentAndRemovesResolvedCluster() throws Exception {
    long changedRawId = insertRaw("IDEM1", validJob("IDEM1", "Java Engineer", "Identical"));
    insertRaw("IDEM2", validJob("IDEM2", "Java Engineer", "Identical"));
    launch(null);
    var timestamps =
        jdbcTemplate.queryForMap(
            """
                SELECT c.created_at, c.updated_at, m.added_at, e.detected_at
                FROM duplicate_cluster c
                JOIN duplicate_cluster_member m ON m.cluster_id = c.id AND m.is_canonical
                JOIN duplicate_match_evidence e ON e.cluster_id = c.id
                """);

    launch(null);

    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT c.created_at, c.updated_at, m.added_at, e.detected_at
                FROM duplicate_cluster c
                JOIN duplicate_cluster_member m ON m.cluster_id = c.id AND m.is_canonical
                JOIN duplicate_match_evidence e ON e.cluster_id = c.id
                """))
        .isEqualTo(timestamps);
    String changed = validJob("IDEM1", "Java Engineer", "Now materially different");
    jdbcTemplate.update(
        """
                UPDATE raw_job_posting
                SET raw_payload_json = CAST(? AS jsonb), payload_hash = ?, processing_status = 'NEW',
                    processing_reason = NULL, processed_at = NULL
                WHERE id = ?
                """,
        changed,
        sha256(changed),
        changedRawId);

    launch(null);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM duplicate_cluster", Integer.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM duplicate_cluster_member", Integer.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM duplicate_match_evidence", Integer.class))
        .isZero();
  }

  @Test
  void duplicateStepRollsBackAndRestartsSameJobInstance() throws Exception {
    insertRaw("RESTART-DUP1", validJob("RESTART-DUP1", "Backend Engineer", "Exact duplicate"));
    insertRaw("RESTART-DUP2", validJob("RESTART-DUP2", "Backend Engineer", "Exact duplicate"));

    JobExecution failed = launch(null, true);

    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM duplicate_cluster", Integer.class))
        .isZero();
    assertThat(failed.getStepExecutions())
        .anySatisfy(
            step -> {
              assertThat(step.getStepName()).isEqualTo("exactDuplicateDetectionStep");
              assertThat(step.getStatus()).isEqualTo(BatchStatus.FAILED);
              assertThat(step.getRollbackCount()).isGreaterThanOrEqualTo(1);
            });

    JobExecution restarted = jobOperator.restart(failed);

    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
    assertThat(restarted.getId()).isNotEqualTo(failed.getId());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM duplicate_cluster", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM duplicate_cluster_member", Integer.class))
        .isEqualTo(2);
    assertThat(restarted.getStepExecutions())
        .extracting(step -> step.getStepName())
        .doesNotContain("jobNormalizationStep", "skillExtractionStep");
  }

  @Test
  void persistsExplainableFuzzySimilarityButDoesNotRepeatExactPairs() throws Exception {
    insertRaw(
        "FUZZY1",
        validJob(
            "FUZZY1",
            "Senior Java Backend Engineer",
            "Build payments APIs with Spring Boot and Kafka"));
    insertRaw(
        "FUZZY2",
        validJob(
            "FUZZY2", "Java Backend Engineer", "Build payment APIs using Spring Boot and Kafka"));
    insertRaw("EXACT1", validJob("EXACT1", "Warehouse Data Analyst", "Exact warehouse role"));
    insertRaw("EXACT2", validJob("EXACT2", "Warehouse Data Analyst", "Exact warehouse role"));
    insertRaw(
        "UNRELATED",
        validJob(
            "UNRELATED", "Digital Marketing Manager", "Run brand campaigns and social channels"));

    JobExecution execution = launch(null);

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM duplicate_cluster", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_similarity", Integer.class))
        .isEqualTo(1);
    var similarity =
        jdbcTemplate.queryForMap(
            """
                SELECT s.id, left_job.external_job_id AS left_external_id,
                       right_job.external_job_id AS right_external_id,
                       s.overall_score, s.decision, s.explanation
                FROM job_similarity s
                JOIN normalized_job left_job ON left_job.id=s.left_job_id
                JOIN normalized_job right_job ON right_job.id=s.right_job_id
                """);
    assertThat(similarity)
        .containsEntry("left_external_id", "FUZZY1")
        .containsEntry("right_external_id", "FUZZY2");
    assertThat((BigDecimal) similarity.get("overall_score"))
        .isGreaterThanOrEqualTo(BigDecimal.valueOf(75));
    assertThat((String) similarity.get("explanation"))
        .contains("title=", "description=", "company=", "weightedScore=");

    var similarities = duplicateQueryController.similarities(null, BigDecimal.valueOf(75));
    assertThat(similarities).hasSize(1);
    long similarityId = ((Number) similarities.getFirst().get("id")).longValue();
    assertThat(duplicateQueryController.similarity(similarityId))
        .containsKeys("title_score", "description_score", "company_score", "explanation");
  }

  @Test
  void fuzzySimilarityIsIdempotentAndRemovedWhenContentDiverges() throws Exception {
    long changedRawId =
        insertRaw(
            "FUZZY-IDEM1",
            validJob(
                "FUZZY-IDEM1",
                "Senior Java Backend Engineer",
                "Build payments APIs with Spring Boot and Kafka"));
    insertRaw(
        "FUZZY-IDEM2",
        validJob(
            "FUZZY-IDEM2",
            "Java Backend Engineer",
            "Build payment APIs using Spring Boot and Kafka"));
    launch(null);
    var calculatedAt =
        jdbcTemplate.queryForObject(
            "SELECT calculated_at FROM job_similarity", java.sql.Timestamp.class);

    launch(null);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT calculated_at FROM job_similarity", java.sql.Timestamp.class))
        .isEqualTo(calculatedAt);
    String changed =
        validJob(
            "FUZZY-IDEM1", "Digital Marketing Manager", "Run brand campaigns and social channels");
    jdbcTemplate.update(
        """
                UPDATE raw_job_posting
                SET raw_payload_json = CAST(? AS jsonb), payload_hash = ?, processing_status = 'NEW',
                    processing_reason = NULL, processed_at = NULL
                WHERE id = ?
                """,
        changed,
        sha256(changed),
        changedRawId);

    launch(null);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_similarity", Integer.class))
        .isZero();
  }

  @Test
  void fuzzyStepRollsBackAndRestartsWithoutReplayingCompletedSteps() throws Exception {
    insertRaw(
        "FUZZY-RESTART1",
        validJob(
            "FUZZY-RESTART1",
            "Senior Java Backend Engineer",
            "Build payments APIs with Spring Boot and Kafka"));
    insertRaw(
        "FUZZY-RESTART2",
        validJob(
            "FUZZY-RESTART2",
            "Java Backend Engineer",
            "Build payment APIs using Spring Boot and Kafka"));

    JobExecution failed = launch(null, false, true);

    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_similarity", Integer.class))
        .isZero();
    assertThat(failed.getStepExecutions())
        .anySatisfy(
            step -> {
              assertThat(step.getStepName()).isEqualTo("fuzzyDuplicateDetectionStep");
              assertThat(step.getStatus()).isEqualTo(BatchStatus.FAILED);
              assertThat(step.getRollbackCount()).isGreaterThanOrEqualTo(1);
            });

    JobExecution restarted = jobOperator.restart(failed);

    assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_similarity", Integer.class))
        .isEqualTo(1);
    assertThat(restarted.getStepExecutions())
        .extracting(step -> step.getStepName())
        .doesNotContain(
            "jobNormalizationStep", "skillExtractionStep", "exactDuplicateDetectionStep");
  }

  private JobExecution launch(Long failAfterItems) throws Exception {
    return launch(failAfterItems, false);
  }

  private JobExecution launch(Long failAfterItems, boolean failDuplicateDetection)
      throws Exception {
    return launch(failAfterItems, failDuplicateDetection, false);
  }

  private JobExecution launch(
      Long failAfterItems, boolean failDuplicateDetection, boolean failFuzzyDetection)
      throws Exception {
    JobParametersBuilder parameters =
        new JobParametersBuilder()
            .addLocalDate(
                "businessDate",
                LocalDate.of(2050, 1, 1).plusDays(DATE_SEQUENCE.incrementAndGet()),
                true)
            .addString("normalizationVersion", "v1", true)
            .addString("duplicateDetectionVersion", "fuzzy-v1", true);
    if (failAfterItems != null) {
      parameters.addLong("failAfterItems", failAfterItems, false);
    }
    if (failDuplicateDetection) {
      parameters.addLong("failDuplicateDetection", 1L, false);
    }
    if (failFuzzyDetection) {
      parameters.addLong("failFuzzyDetection", 1L, false);
    }
    return jobOperator.start(intelligenceJob, parameters.toJobParameters());
  }

  private long insertRaw(String externalId, String rawJson) throws Exception {
    return jdbcTemplate.queryForObject(
        """
                INSERT INTO raw_job_posting (
                    source, external_job_id, search_profile_id, source_fetch_run_id,
                    source_url, payload_hash, raw_payload_json, job_execution_id
                ) VALUES ('ADZUNA', ?, 'INT001', ?, ?, ?, CAST(? AS jsonb), ?)
                RETURNING id
                """,
        Long.class,
        externalId,
        sourceFetchRunId,
        "https://example/jobs/" + externalId,
        sha256(rawJson),
        rawJson,
        discoveryExecutionId);
  }

  private static String validJob(String id, String title, String description) {
    return """
                {"id":"%s","title":"%s","company":{"display_name":"Example Bank"},
                 "location":{"display_name":"Singapore"},"description":"%s",
                 "contract_type":"permanent","salary_min":90000,"salary_max":120000,
                 "salary_currency":"SGD","remote_type":"hybrid",
                 "created":"2026-08-20T10:15:30Z","redirect_url":"https://example/jobs/%s"}
                """
        .formatted(id, title, description, id);
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
