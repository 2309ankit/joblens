package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.onboarding.OnboardingRepository;
import com.ankit.joblens.onboarding.SearchPreferences;
import com.ankit.joblens.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class RoleAwareRankingIntegrationTests {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("joblens_role_ranking_test")
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
  @Autowired private JobScoreCalculator calculator;

  @Test
  void separatesFrontendAndBackendAndChoosesTheBestOfMultipleTargets() {
    long candidateId =
        candidate(
            "Frontend Engineer, Backend Engineer",
            List.of("React", "TypeScript", "Java", "Spring Boot"));
    RoleRankingContext context = calculator.context(candidateId);

    JobScore frontend =
        calculator.calculate(
            job(
                9001,
                "Senior Frontend Engineer",
                "Build accessible React and TypeScript interfaces with HTML and CSS."),
            context);
    JobScore backend =
        calculator.calculate(
            job(
                9002,
                "Senior Backend Engineer",
                "Build Java REST services with Spring Boot, SQL and Kafka."),
            context);

    assertThat(frontend.bestRole().targetRoleName()).isEqualTo("Frontend Engineer");
    assertThat(frontend.bestRole().calibrationPackCode()).isEqualTo("FRONTEND");
    assertThat(backend.bestRole().targetRoleName()).isEqualTo("Backend Engineer");
    assertThat(backend.bestRole().calibrationPackCode()).isEqualTo("BACKEND");
    assertThat(frontend.roleScores()).hasSize(2);
    assertThat(backend.roleScores()).hasSize(2);
  }

  @Test
  void loadsAiSalesAndCustomerSuccessCalibrationOverlays() {
    JobScore ai =
        score(
            "Machine Learning Engineer",
            List.of("Python", "Machine Learning", "SQL"),
            "Applied ML Engineer",
            "Develop Python machine learning systems using SQL and Docker.");
    JobScore sales =
        score(
            "Sales Manager",
            List.of("Sales", "CRM"),
            "Regional Sales Manager",
            "Lead enterprise sales forecasting and CRM operations.");
    JobScore customerSuccess =
        score(
            "Customer Success Manager",
            List.of("Customer Service", "CRM"),
            "Client Success Manager",
            "Own customer service, retention and CRM adoption.");

    assertThat(ai.bestRole().calibrationPackCode()).isEqualTo("AI_ML");
    assertThat(sales.bestRole().calibrationPackCode()).isEqualTo("SALES_CUSTOMER_SUCCESS");
    assertThat(customerSuccess.bestRole().calibrationPackCode())
        .isEqualTo("SALES_CUSTOMER_SUCCESS");
    assertThat(ai.bestRole().policyVersion()).isEqualTo(JobScoreCalculator.POLICY_VERSION);
  }

  @Test
  void usesUniversalPolicyForOtherRolesAndDoesNotDiscardMissingCoreSkills() {
    JobScore nurse =
        score(
            "Registered Nurse",
            List.of("Nursing"),
            "Registered Nurse",
            "Provide nursing care and maintain patient records.");
    JobScore frontendWithoutCore =
        score(
            "Frontend Engineer",
            List.of("Figma"),
            "Frontend Engineer",
            "Collaborate with designers to deliver web experiences.");

    assertThat(nurse.bestRole().calibrationPackCode()).isNull();
    assertThat(nurse.bestRole().calibrationPackName()).isEqualTo("Universal policy");
    assertThat(nurse.total()).isPositive();
    assertThat(frontendWithoutCore.total()).isPositive();
    assertThat(frontendWithoutCore.reasons())
        .filteredOn(reason -> reason.category().equals("CALIBRATED_CORE_SKILLS"))
        .singleElement()
        .satisfies(reason -> assertThat(reason.points()).isZero());
  }

  private JobScore score(String role, List<String> skills, String title, String description) {
    return calculator.calculate(
        job(Math.abs(UUID.randomUUID().getLeastSignificantBits()), title, description),
        candidate(role, skills));
  }

  private long candidate(String roles, List<String> skills) {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "a".repeat(64));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Ranking candidate");
    onboarding.addSkills(profileId, skills);
    onboarding.savePreferences(
        workspaceId,
        profileId,
        new SearchPreferences(
            roles,
            "",
            "Singapore",
            "ranking test",
            "SG | Singapore",
            1,
            "PERMANENT",
            "REMOTE,HYBRID,ONSITE"));
    return onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());
  }

  private static NormalizedJobView job(long id, String title, String description) {
    return new NormalizedJobView(
        id,
        "ADZUNA",
        Long.toString(id),
        title,
        "Example Company",
        "Singapore",
        description,
        "PERMANENT",
        null,
        null,
        null,
        "HYBRID",
        OffsetDateTime.now(),
        "https://example.test/jobs/" + id,
        "a".repeat(64));
  }
}
