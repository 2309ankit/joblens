package com.ankit.joblens.searchprofile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
class SearchProfileImportJobIntegrationTests {

    private static final AtomicInteger DATE_SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("joblens_test")
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
    @Qualifier("searchProfileImportJob")
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void clearBusinessTables() {
        jdbcTemplate.update("DELETE FROM search_profile_rejection");
        jdbcTemplate.update("DELETE FROM search_profile");
    }

    @Test
    void importsValidCsvAndCompletesWithMetadata() throws Exception {
        JobExecution execution = launch(Path.of("data/import/search-profiles.csv"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_profile", Integer.class)).isEqualTo(4);
        var step = execution.getStepExecutions().iterator().next();
        assertThat(step.getReadCount()).isEqualTo(4);
        assertThat(step.getWriteCount()).isEqualTo(4);
        assertThat(step.getSkipCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM batch_job_execution WHERE job_execution_id = ?",
                Integer.class, execution.getId())).isEqualTo(1);
    }

    @Test
    void persistsInvalidRowsAsRejections() throws Exception {
        JobExecution execution = launch(Path.of("src/test/resources/fixtures/search-profiles-invalid.csv"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_profile", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_profile_rejection", Integer.class)).isEqualTo(5);
        assertThat(execution.getStepExecutions().iterator().next().getProcessSkipCount()).isEqualTo(5);
        assertThat(jdbcTemplate.queryForList(
                "SELECT rejection_reason FROM search_profile_rejection", String.class))
                .anyMatch(reason -> reason.contains("profile_id is required"))
                .anyMatch(reason -> reason.contains("Unsupported source"));
    }

    @Test
    void upsertsDuplicateProfileWithinImport() throws Exception {
        Path input = writeCsv("duplicate.csv", """
                profile_id,source,source_key,keywords,location,include_skills,exclude_skills,employment_type,active
                SP200,ADZUNA,sg,"first value",Singapore,java,"",any,true
                SP200,ADZUNA,sg,"second value",Singapore,"java|kafka","",permanent,false
                """);

        JobExecution execution = launch(input);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_profile", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT keywords FROM search_profile WHERE profile_id='SP200'", String.class)).isEqualTo("second value");
    }

    @Test
    void secondImportUpdatesExistingProfile() throws Exception {
        Path input = writeCsv("update.csv", """
                profile_id,source,source_key,keywords,location,include_skills,exclude_skills,employment_type,active
                SP300,ADZUNA,sg,"original",Singapore,java,"",any,true
                """);
        JobExecution first = launch(input);
        var createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM search_profile WHERE profile_id='SP300'", java.sql.Timestamp.class);

        Thread.sleep(10);
        Files.writeString(input, """
                profile_id,source,source_key,keywords,location,include_skills,exclude_skills,employment_type,active
                SP300,ADZUNA,sg,"changed",Singapore,"java|kafka","",permanent,false
                """);
        JobExecution second = launch(input);

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_profile", Integer.class)).isEqualTo(1);
        var updated = jdbcTemplate.queryForMap("""
                SELECT keywords, employment_type, active, created_at, updated_at
                FROM search_profile WHERE profile_id='SP300'
                """);
        assertThat(updated)
                .containsEntry("keywords", "changed")
                .containsEntry("employment_type", "PERMANENT")
                .containsEntry("active", false)
                .containsEntry("created_at", createdAt);
        assertThat((java.sql.Timestamp) updated.get("updated_at")).isAfter(createdAt);
    }

    @Test
    void restartsFailedInstanceFromCommittedReaderCheckpoint() throws Exception {
        Path input = Path.of("data/import/search-profiles-restart.csv").toAbsolutePath().normalize();
        LocalDate businessDate = LocalDate.of(2031, 1, 1).plusDays(DATE_SEQUENCE.incrementAndGet());
        var failedParameters = new JobParametersBuilder()
                .addString("inputFile", input.toString(), true)
                .addLocalDate("businessDate", businessDate, true)
                .addLong("failOnRow", 4L, false)
                .toJobParameters();

        JobExecution failed = jobOperator.start(job, failedParameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_profile", Integer.class)).isEqualTo(2);

        var restartParameters = new JobParametersBuilder()
                .addString("inputFile", input.toString(), true)
                .addLocalDate("businessDate", businessDate, true)
                .toJobParameters();
        JobExecution restarted = jobOperator.start(job, restartParameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_profile", Integer.class)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM search_profile WHERE profile_id IN ('SR001','SR002')", Integer.class)).isEqualTo(2);
        assertThat(restarted.getStepExecutions().iterator().next().getWriteCount()).isEqualTo(4);
    }

    private JobExecution launch(Path input) throws Exception {
        LocalDate businessDate = LocalDate.of(2030, 1, 1).plusDays(DATE_SEQUENCE.incrementAndGet());
        return jobOperator.start(job, new JobParametersBuilder()
                .addString("inputFile", input.toAbsolutePath().normalize().toString(), true)
                .addLocalDate("businessDate", businessDate, true)
                .toJobParameters());
    }

    private Path writeCsv(String filename, String content) throws Exception {
        Path file = tempDirectory.resolve(filename);
        Files.writeString(file, content);
        return file;
    }
}
