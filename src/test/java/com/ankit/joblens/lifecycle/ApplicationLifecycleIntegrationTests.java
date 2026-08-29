package com.ankit.joblens.lifecycle;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import com.ankit.joblens.batchapi.ApplicationController;
import com.ankit.joblens.batchapi.ApplicationFollowUpController;
import com.ankit.joblens.batchapi.ApplicationTransitionRequest;
import com.ankit.joblens.batchapi.CreateApplicationRequest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ApplicationLifecycleIntegrationTests {

    private static final AtomicInteger DATE_SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("joblens_lifecycle_test")
            .withUsername("joblens")
            .withPassword("joblens-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("applicationFollowUpJob")
    private Job followUpJob;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationLifecycleService service;

    @Autowired
    private ApplicationController applicationController;

    @Autowired
    private ApplicationFollowUpController followUpController;

    private long infrastructureExecutionId;
    private long sourceFetchRunId;

    @BeforeEach
    void cleanAndCreateInfrastructure() throws Exception {
        jdbc.update("DELETE FROM normalized_job");
        jdbc.update("DELETE FROM raw_job_posting");
        jdbc.update("DELETE FROM source_fetch_run");
        jdbc.update("DELETE FROM search_profile_rejection");
        jdbc.update("DELETE FROM search_profile");

        JobExecution infrastructure = launch(LocalDate.of(2080, 1, 1)
                .plusDays(DATE_SEQUENCE.incrementAndGet()), null);
        infrastructureExecutionId = infrastructure.getId();
        jdbc.update("""
                INSERT INTO search_profile (
                    profile_id, source, source_key, keywords, location,
                    include_skills, exclude_skills, employment_type, active
                ) VALUES ('LIFE', 'ADZUNA', 'sg', 'java', 'Singapore', '', '', 'ANY', true)
                """);
        sourceFetchRunId = jdbc.queryForObject("""
                INSERT INTO source_fetch_run (
                    source, search_profile_id, status, completed_at, pages_fetched,
                    records_received, next_page, job_instance_id, job_execution_id
                ) VALUES ('ADZUNA', 'LIFE', 'COMPLETED', CURRENT_TIMESTAMP, 1, 0, 2, ?, ?)
                RETURNING id
                """, Long.class, infrastructure.getJobInstanceId(), infrastructure.getId());
    }

    @Test
    void createsApplicationAndEnforcesAuditedTransitions() {
        long jobId = insertNormalizedJob("LIFE-1", "Senior Java Engineer");

        var created = applicationController.create(
                new CreateApplicationRequest(jobId, LocalDate.of(2030, 1, 1), "Saved for review"));
        long applicationId = ((Number) created.get("id")).longValue();
        assertThat(created).containsEntry("status", "SAVED");

        var applied = applicationController.transition(applicationId,
                new ApplicationTransitionRequest("applied", LocalDate.of(2030, 1, 3), "Applied online"));

        assertThat(applied)
                .containsEntry("status", "APPLIED")
                .containsEntry("applied_on", LocalDate.of(2030, 1, 3));
        assertThat((java.util.List<?>) applied.get("history")).hasSize(2);
        assertThatThrownBy(() -> service.transition(applicationId, ApplicationStatus.ACCEPTED,
                LocalDate.of(2030, 1, 4), null))
                .isInstanceOf(LifecycleConflictException.class);
        assertThatThrownBy(() -> service.transition(applicationId, ApplicationStatus.SCREENING,
                LocalDate.of(2029, 12, 31), null))
                .isInstanceOf(LifecycleValidationException.class);
    }

    @Test
    void generatesReconcilesCompletesAndPreservesFollowUpsIdempotently() throws Exception {
        long jobId = insertNormalizedJob("LIFE-2", "Backend Engineer");
        long applicationId = service.create(jobId, LocalDate.of(2030, 2, 1), null);
        service.transition(applicationId, ApplicationStatus.APPLIED, LocalDate.of(2030, 2, 2), null);

        JobExecution first = launch(LocalDate.of(2030, 2, 10), null);
        var firstFollowUp = jdbc.queryForMap("""
                SELECT id, follow_up_type, due_date, status, created_at, updated_at
                FROM application_follow_up
                """);
        long followUpId = ((Number) firstFollowUp.get("id")).longValue();
        Object createdAt = firstFollowUp.get("created_at");
        Object updatedAt = firstFollowUp.get("updated_at");

        JobExecution rerun = launch(LocalDate.of(2030, 2, 11), null);

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(rerun.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForMap("SELECT created_at, updated_at FROM application_follow_up WHERE id=?", followUpId))
                .containsEntry("created_at", createdAt)
                .containsEntry("updated_at", updatedAt);
        assertThat(firstFollowUp)
                .containsEntry("follow_up_type", "APPLICATION_CHECK_IN")
                .containsEntry("due_date", java.sql.Date.valueOf("2030-02-09"))
                .containsEntry("status", "OPEN");

        service.transition(applicationId, ApplicationStatus.SCREENING, LocalDate.of(2030, 2, 12), null);
        launch(LocalDate.of(2030, 2, 12), null);

        assertThat(jdbc.queryForList("""
                SELECT follow_up_type || ':' || status
                FROM application_follow_up ORDER BY id
                """, String.class))
                .containsExactly("APPLICATION_CHECK_IN:CANCELLED", "RECRUITER_CHECK_IN:OPEN");
        long recruiterFollowUpId = jdbc.queryForObject("""
                SELECT id FROM application_follow_up WHERE status='OPEN'
                """, Long.class);
        var completed = followUpController.complete(recruiterFollowUpId, LocalDate.of(2030, 2, 15));
        assertThat(completed)
                .containsEntry("status", "COMPLETED")
                .containsEntry("completed_on", LocalDate.of(2030, 2, 15));
    }

    @Test
    void rollsBackInjectedFailureAndRestartsSameJobInstance() throws Exception {
        for (int index = 1; index <= 2; index++) {
            long jobId = insertNormalizedJob("RESTART-" + index, "Engineer " + index);
            long applicationId = service.create(jobId, LocalDate.of(2030, 3, 1), null);
            service.transition(applicationId, ApplicationStatus.APPLIED, LocalDate.of(2030, 3, 2), null);
        }

        JobExecution failed = launch(LocalDate.of(2030, 3, 10), 1L);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_follow_up", Integer.class)).isZero();
        assertThat(failed.getStepExecutions()).singleElement().satisfies(step -> {
            assertThat(step.getStepName()).isEqualTo("applicationFollowUpGenerationStep");
            assertThat(step.getRollbackCount()).isGreaterThanOrEqualTo(1);
        });

        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_follow_up", Integer.class)).isEqualTo(2);
    }

    private JobExecution launch(LocalDate businessDate, Long failAfterApplications) throws Exception {
        var parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", businessDate, true)
                .addString("followUpVersion", ApplicationFollowUpTasklet.GENERATION_VERSION, true);
        if (failAfterApplications != null) {
            parameters.addLong("failAfterApplications", failAfterApplications, false);
        }
        return jobOperator.start(followUpJob, parameters.toJobParameters());
    }

    private long insertNormalizedJob(String externalId, String title) {
        long rawId = jdbc.queryForObject("""
                INSERT INTO raw_job_posting (
                    source, external_job_id, search_profile_id, source_fetch_run_id,
                    source_url, payload_hash, raw_payload_json, job_execution_id, processing_status
                ) VALUES ('ADZUNA', ?, 'LIFE', ?, ?, ?, CAST(? AS jsonb), ?, 'NORMALIZED')
                RETURNING id
                """, Long.class, externalId, sourceFetchRunId, "https://example/jobs/" + externalId,
                "a".repeat(64), "{\"id\":\"" + externalId + "\",\"title\":\"" + title + "\"}",
                infrastructureExecutionId);
        return jdbc.queryForObject("""
                INSERT INTO normalized_job (
                    raw_job_posting_id, source, external_job_id, title, company,
                    normalized_content_hash
                ) VALUES (?, 'ADZUNA', ?, ?, 'Example Bank', ?)
                RETURNING id
                """, Long.class, rawId, externalId, title,
                Integer.toHexString(externalId.hashCode()).replace("-", "0").repeat(16).substring(0, 64));
    }
}
