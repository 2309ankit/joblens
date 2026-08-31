package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
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

@SpringBootTest
@Testcontainers
class EscoTaxonomyImportIntegrationTests {
  private static final MockWebServer ESCO = new MockWebServer();

  static {
    try {
      ESCO.start();
    } catch (java.io.IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("joblens_esco_test")
          .withUsername("joblens")
          .withPassword("joblens-test");

  @AfterAll
  static void stopServer() throws Exception {
    ESCO.shutdown();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("joblens.esco.base-url", () -> ESCO.url("/api").toString());
    registry.add("joblens.esco.page-size", () -> "1");
    registry.add("joblens.esco.retry-attempts", () -> "1");
    registry.add("joblens.esco.timeout", () -> "2s");
  }

  @Autowired private JobOperator jobOperator;

  @Autowired
  @Qualifier("escoTaxonomyImportJob")
  private Job importJob;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clean() throws Exception {
    jdbc.update("DELETE FROM taxonomy_release");
    while (ESCO.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
      // Drain requests from the preceding test.
    }
  }

  @Test
  void importsBothConceptTypesWithAliasesAndActivatesTheReleaseIdempotently() throws Exception {
    ESCO.enqueue(
        json(
            "skill",
            "http://data.europa.eu/esco/skill/account-management",
            "Account Management",
            "Account Farming"));
    ESCO.enqueue(
        json(
            "occupation",
            "http://data.europa.eu/esco/occupation/account-executive",
            "Account Executive",
            "Account Manager"));

    JobExecution execution =
        jobOperator.start(
            importJob,
            new JobParametersBuilder()
                .addString("taxonomyVersion", "test-1", true)
                .toJobParameters());

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM taxonomy_release WHERE source='ESCO' AND version='test-1' AND active",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM skill WHERE external_uri=? AND taxonomy_source='ESCO'",
                Integer.class,
                "http://data.europa.eu/esco/skill/account-management"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM skill_alias WHERE alias_name='Account Farming'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_catalog WHERE external_uri=?",
                Integer.class,
                "http://data.europa.eu/esco/occupation/account-executive"))
        .isEqualTo(1);
  }

  private static MockResponse json(
      String type, String uri, String preferredLabel, String alternativeLabel) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            "{\"offset\":0,\"limit\":1,\"total\":1,\"_embedded\":{\"results\":[{"
                + "\"uri\":\""
                + uri
                + "\",\"title\":\""
                + preferredLabel
                + "\",\"preferredLabel\":{\"en\":\""
                + preferredLabel
                + "\"},\"alternativeLabel\":{\"en\":[\""
                + alternativeLabel
                + "\"]},\"description\":{\"en\":{\"literal\":\""
                + type
                + "\"}}}]}}");
  }
}
