package com.ankit.joblens.discovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
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

@SpringBootTest
@Testcontainers
class JobDiscoveryIntegrationTests {

    private static final AtomicInteger BUSINESS_DATE_SEQUENCE = new AtomicInteger();
    private static final MockWebServer ADZUNA = new MockWebServer();

    static {
        try {
            ADZUNA.start();
        }
        catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("joblens_discovery_test")
            .withUsername("joblens")
            .withPassword("joblens-test");

    @AfterAll
    static void stopMockAdzuna() throws Exception {
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
        registry.add("joblens.adzuna.page-size", () -> "2");
        registry.add("joblens.adzuna.max-pages", () -> "10");
        registry.add("joblens.adzuna.retry-attempts", () -> "3");
        registry.add("joblens.adzuna.retry-backoff", () -> "1ms");
        registry.add("joblens.adzuna.timeout", () -> "2s");
    }

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("jobDiscoveryJob")
    private Job discoveryJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBusinessData() throws Exception {
        jdbcTemplate.update("DELETE FROM raw_job_posting");
        jdbcTemplate.update("DELETE FROM source_fetch_run");
        jdbcTemplate.update("DELETE FROM search_profile_rejection");
        jdbcTemplate.update("DELETE FROM search_profile");
        while (ADZUNA.takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS) != null) {
            // Drain recorded requests so each test can assert its own request count through takeRequest.
        }
    }

    @Test
    void discoversOnePageAndPreservesRawJson() throws Exception {
        insertProfile("D001", true);
        ADZUNA.enqueue(json(200, response(1, job("J1", "Original", 100, true))));

        JobExecution execution = launch("D001");

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status, pages_fetched, records_received FROM source_fetch_run
                WHERE search_profile_id='D001'
                """))
                .containsEntry("status", "COMPLETED")
                .containsEntry("pages_fetched", 1)
                .containsEntry("records_received", 1);
        var raw = jdbcTemplate.queryForMap("""
                SELECT external_job_id, raw_payload_json->>'title' AS title,
                       raw_payload_json->'custom'->>'keep' AS custom_value,
                       payload_hash, processing_status
                FROM raw_job_posting WHERE external_job_id='J1'
                """);
        assertThat(raw)
                .containsEntry("external_job_id", "J1")
                .containsEntry("title", "Original")
                .containsEntry("custom_value", "true")
                .containsEntry("processing_status", "PENDING");
        assertThat(raw.get("payload_hash")).isEqualTo(hash(job("J1", "Original", 100, true)));
    }

    @Test
    void fetchesMultiplePagesUntilEmptyPage() throws Exception {
        insertProfile("D002", true);
        ADZUNA.enqueue(json(200, responseWithoutCount(job("J1", "One", 1, true), job("J2", "Two", 2, true))));
        ADZUNA.enqueue(json(200, responseWithoutCount()));

        JobExecution execution = launch("D002");

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM raw_job_posting", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForMap("SELECT pages_fetched, records_received FROM source_fetch_run"))
                .containsEntry("pages_fetched", 2)
                .containsEntry("records_received", 2);
        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/1?");
        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/2?");
    }

    @Test
    void ignoresInactiveProfileWithoutCallingSource() throws Exception {
        insertProfile("D003", false);

        JobExecution execution = launch(null);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM source_fetch_run", Integer.class)).isZero();
        assertThat(ADZUNA.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void unchangedAndChangedPayloadAreIdempotentAndPreserveFirstSeen() throws Exception {
        insertProfile("D004", true);
        String original = job("J4", "Original", 100, true);
        ADZUNA.enqueue(json(200, response(1, original)));
        JobExecution first = launch("D004");
        var before = jdbcTemplate.queryForMap("""
                SELECT first_seen_at, last_seen_at, updated_at, payload_hash
                FROM raw_job_posting WHERE external_job_id='J4'
                """);

        Thread.sleep(10);
        ADZUNA.enqueue(json(200, response(1, original)));
        JobExecution unchanged = launch("D004");
        var same = jdbcTemplate.queryForMap("""
                SELECT first_seen_at, last_seen_at, updated_at, payload_hash
                FROM raw_job_posting WHERE external_job_id='J4'
                """);

        Thread.sleep(10);
        String changed = job("J4", "Changed", 200, true);
        ADZUNA.enqueue(json(200, response(1, changed)));
        JobExecution changedExecution = launch("D004");
        var after = jdbcTemplate.queryForMap("""
                SELECT first_seen_at, last_seen_at, updated_at, payload_hash,
                       raw_payload_json->>'title' AS title
                FROM raw_job_posting WHERE external_job_id='J4'
                """);

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(unchanged.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(changedExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM raw_job_posting", Integer.class)).isEqualTo(1);
        assertThat(same.get("first_seen_at")).isEqualTo(before.get("first_seen_at"));
        assertThat(same.get("updated_at")).isEqualTo(before.get("updated_at"));
        assertThat((Timestamp) same.get("last_seen_at")).isAfter((Timestamp) before.get("last_seen_at"));
        assertThat(after.get("first_seen_at")).isEqualTo(before.get("first_seen_at"));
        assertThat((Timestamp) after.get("updated_at")).isAfter((Timestamp) same.get("updated_at"));
        assertThat(after).containsEntry("title", "Changed").containsEntry("payload_hash", hash(changed));
    }

    @Test
    void malformedResponseFailsJobAndFetchRun() throws Exception {
        insertProfile("D005", true);
        ADZUNA.enqueue(json(200, "not-json"));

        JobExecution execution = launch("D005");

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbcTemplate.queryForMap("SELECT status, failure_reason FROM source_fetch_run"))
                .containsEntry("status", "FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT failure_reason FROM source_fetch_run", String.class))
                .contains("MalformedJobSourceResponseException");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM raw_job_posting", Integer.class)).isZero();
    }

    @Test
    void restartsSameInstanceAtFailedPageWithoutReplayingCommittedPages() throws Exception {
        insertProfile("D006", true);
        ADZUNA.enqueue(json(200, response(10, job("R1", "One", 1, true), job("R2", "Two", 2, true))));
        ADZUNA.enqueue(json(200, response(10, job("R3", "Three", 3, true), job("R4", "Four", 4, true))));
        ADZUNA.enqueue(json(503, "{}"));
        ADZUNA.enqueue(json(503, "{}"));
        ADZUNA.enqueue(json(503, "{}"));
        LocalDate businessDate = nextBusinessDate();

        JobExecution failed = launch("D006", businessDate);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM raw_job_posting", Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status, pages_fetched, records_received, next_page FROM source_fetch_run
                """))
                .containsEntry("status", "FAILED")
                .containsEntry("pages_fetched", 2)
                .containsEntry("records_received", 4)
                .containsEntry("next_page", 3);
        assertThat(failed.getStepExecutions().iterator().next().getExecutionContext()
                .getInt(JobDiscoveryTasklet.NEXT_PAGE)).isEqualTo(3);

        ADZUNA.enqueue(json(200, response(5, job("R5", "Five", 5, true))));
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM raw_job_posting", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT external_job_id FROM raw_job_posting GROUP BY external_job_id HAVING count(*) > 1
                ) duplicates
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForMap("SELECT status, pages_fetched, records_received FROM source_fetch_run"))
                .containsEntry("status", "COMPLETED")
                .containsEntry("pages_fetched", 3)
                .containsEntry("records_received", 5);

        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/1?");
        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/2?");
        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/3?");
        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/3?");
        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/3?");
        assertThat(ADZUNA.takeRequest().getPath()).contains("/search/3?");
    }

    private void insertProfile(String profileId, boolean active) {
        jdbcTemplate.update("""
                INSERT INTO search_profile (
                    profile_id, source, source_key, keywords, location,
                    include_skills, exclude_skills, employment_type, active
                ) VALUES (?, 'ADZUNA', 'sg', 'java developer', 'Singapore', 'java', '', 'ANY', ?)
                """, profileId, active);
    }

    private JobExecution launch(String profileId) throws Exception {
        return launch(profileId, nextBusinessDate());
    }

    private JobExecution launch(String profileId, LocalDate businessDate) throws Exception {
        JobParametersBuilder parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", businessDate, true);
        if (profileId != null) {
            parameters.addString("profileId", profileId, true);
        }
        return jobOperator.start(discoveryJob, parameters.toJobParameters());
    }

    private static LocalDate nextBusinessDate() {
        return LocalDate.of(2040, 1, 1).plusDays(BUSINESS_DATE_SEQUENCE.incrementAndGet());
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String response(long count, String... jobs) {
        return "{\"count\":" + count + ",\"results\":[" + String.join(",", jobs) + "]}";
    }

    private static String responseWithoutCount(String... jobs) {
        return "{\"results\":[" + String.join(",", jobs) + "]}";
    }

    private static String job(String id, String title, int salary, boolean customValue) {
        return "{\"id\":\"" + id + "\",\"redirect_url\":\"https://example/jobs/" + id
                + "\",\"title\":\"" + title + "\",\"salary_min\":" + salary
                + ",\"custom\":{\"keep\":" + customValue + "}}";
    }

    private static String hash(String rawJson) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawJson.getBytes(StandardCharsets.UTF_8)));
    }
}
