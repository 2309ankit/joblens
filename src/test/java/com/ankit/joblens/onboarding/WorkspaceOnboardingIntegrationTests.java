package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ankit.joblens.discovery.JoobleProperties;
import com.ankit.joblens.workspace.WorkspaceContext;
import com.ankit.joblens.workspace.WorkspaceRepository;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
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
  @Autowired private ProfileIntelligenceRepository profileIntelligence;
  @Autowired private ProfileIntelligenceExtractor intelligenceExtractor;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;

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

    OnboardingProfile customDraft =
        onboardingService.updateSkills(first, List.of("Financial Modelling"));
    assertThat(customDraft.skills()).containsExactly("Financial Modelling");
    assertThat(onboardingService.latest(first).orElseThrow().skills())
        .containsExactly("Financial Modelling");
    assertThat(profileIntelligence.skillOptions(second, "Financial Modelling")).isEmpty();

    long confirmedCandidate = onboardingService.confirm(first);

    assertThat(confirmedCandidate).isEqualTo(firstCandidate);
    assertThat(candidateSkills(firstCandidate)).containsExactly("Financial Modelling");
    assertThat(candidateSkills(secondCandidate)).containsExactly("AWS");
  }

  @Test
  void persistsExplainableSuggestionsAndKeepsCustomTaxonomyIdempotentAndPrivate() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID otherWorkspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    workspaces.create(otherWorkspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "d".repeat(64));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Inclusive candidate");
    var extraction =
        intelligenceExtractor.extract(
            """
            Maria Santos
            Registered Nurse
            maria@example.com
            Professional Summary
            Patient care and nursing practice.
            Experience
            Registered Nurse — Community Hospital
            Education
            Bachelor of Nursing
            """,
            profileIntelligence.skillDefinitions(workspaceId),
            profileIntelligence.roleDefinitions(workspaceId));
    var frontendExtraction =
        intelligenceExtractor.extract(
            """
            Priya Shah
            priya@example.com
            Professional Summary
            Frontend specialist using React.js and TypeScript.
            Experience
            Senior Front End Developer — Example Retail
            Education
            Bachelor of Design
            """,
            profileIntelligence.skillDefinitions(workspaceId),
            profileIntelligence.roleDefinitions(workspaceId));
    assertThat(frontendExtraction.skills())
        .extracting(ProfileIntelligenceExtractor.DetectedSkill::name)
        .contains("React", "TypeScript");
    assertThat(frontendExtraction.roles())
        .extracting(ProfileIntelligenceExtractor.DetectedRole::name)
        .contains("Frontend Engineer");

    profileIntelligence.saveSuggestions(profileId, extraction);
    profileIntelligence.saveSuggestions(profileId, extraction);
    onboarding.addSkills(
        profileId,
        extraction.skills().stream()
            .map(ProfileIntelligenceExtractor.DetectedSkill::name)
            .toList());

    ProfileIntelligence intelligence = profileIntelligence.intelligence(workspaceId, profileId);
    assertThat(intelligence.skillSuggestions())
        .filteredOn(suggestion -> suggestion.name().equals("Nursing"))
        .singleElement()
        .satisfies(suggestion -> assertThat(suggestion.evidence()).contains("Registered Nurse"));
    assertThat(intelligence.roleSuggestions())
        .filteredOn(suggestion -> suggestion.name().equals("Registered Nurse"))
        .singleElement()
        .satisfies(
            suggestion -> {
              assertThat(suggestion.evidenceSource()).isEqualTo("RESUME_HEADLINE");
              assertThat(suggestion.confidence()).isEqualByComparingTo("0.950");
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM workspace_profile_role_suggestion WHERE profile_version_id=?",
                Integer.class,
                profileId))
        .isEqualTo(1);
    mvc.perform(
            get("/setup").cookie(new Cookie(WorkspaceContext.COOKIE_NAME, workspaceId.toString())))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/app/index.html"));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString("/api/candidate-profile/intelligence")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("/api/candidate-profile/countries")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "/api/candidate-profile/sectors/catalog")));

    onboardingService.completeSetup(
        workspaceId,
        List.of("Nursing", "Clinical Documentation"),
        new SearchPreferences(
            "Registered Nurse, Clinical Care Lead",
            "healthcare",
            "Singapore",
            "registered nurse",
            "SG | Singapore",
            2,
            "PERMANENT",
            "ONSITE"));
    onboardingService.updateSkills(workspaceId, List.of("Nursing", "Clinical Documentation"));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM skill WHERE created_by_workspace_id=? AND canonical_name='Clinical Documentation'",
                Integer.class,
                workspaceId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_catalog WHERE created_by_workspace_id=? AND canonical_name='Clinical Care Lead'",
                Integer.class,
                workspaceId))
        .isEqualTo(1);
    assertThat(profileIntelligence.skillOptions(otherWorkspaceId, "Clinical Documentation"))
        .isEmpty();
    assertThat(profileIntelligence.roleOptions(otherWorkspaceId, "Clinical Care Lead")).isEmpty();
    assertThat(profileIntelligence.sectorOptions(otherWorkspaceId, "healthcare"))
        .extracting(SectorOption::name)
        .contains("Healthcare");

    jdbc.update("DELETE FROM workspace WHERE id=?", workspaceId);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM skill WHERE created_by_workspace_id=?",
                Integer.class,
                workspaceId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_catalog WHERE created_by_workspace_id=?",
                Integer.class,
                workspaceId))
        .isZero();
  }

  @Test
  void acceptsAStructurallyValidResumeWhenNoSeededSkillOrRoleMatches() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    byte[] docx =
        docx(
            """
            Jordan Lee
            jordan@example.com
            Professional Summary
            Culinary professional serving community events and private functions.
            Experience
            Head Chef — Neighbourhood Kitchen, 2021 - Present
            Planned menus and supervised daily food preparation.
            Education
            Diploma in Culinary Arts
            """);

    OnboardingProfile profile =
        onboardingService.upload(
            workspaceId,
            new MockMultipartFile(
                "file",
                "jordan-resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx));

    assertThat(profile.status()).isEqualTo("DRAFT");
    assertThat(profile.skills()).isEmpty();
    assertThat(onboardingService.intelligence(workspaceId).skillSuggestions()).isEmpty();
    assertThat(onboardingService.intelligence(workspaceId).roleSuggestions()).isEmpty();
    ResumeReadinessAssessment readiness = onboardingService.readiness(workspaceId);
    assertThat(readiness.status()).isEqualTo("READY");
    assertThat(readiness.assessmentVersion()).isEqualTo("readability-v1");
    assertThat(readiness.findings())
        .extracting(ResumeReadinessAssessment.Finding::code)
        .contains("CONTACT_DETAILS_FOUND", "EDUCATION_SECTION_FOUND", "DOCX_SIMPLE_LAYOUT");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM resume_readiness_assessment WHERE profile_version_id=?",
                Integer.class,
                profile.id()))
        .isEqualTo(1);
  }

  @Test
  void resubmittingTheSameResumeReusesTheExistingDraftInsteadOfDuplicatingIt() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    byte[] docx =
        docx(
            """
            Jordan Lee
            jordan@example.com
            Professional Summary
            Culinary professional serving community events and private functions.
            Experience
            Head Chef — Neighbourhood Kitchen, 2021 - Present
            Planned menus and supervised daily food preparation.
            Education
            Diploma in Culinary Arts
            """);

    OnboardingProfile first =
        onboardingService.upload(
            workspaceId,
            new MockMultipartFile(
                "file",
                "jordan-resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx));
    OnboardingProfile second =
        onboardingService.upload(
            workspaceId,
            new MockMultipartFile(
                "file",
                "jordan-resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx));

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM workspace_profile_version WHERE workspace_id=? AND status='DRAFT'",
                Integer.class,
                workspaceId))
        .isEqualTo(1);
  }

  @Test
  void preservesSuspiciousReadableUploadAndRequiresAcknowledgementBeforeActivation()
      throws Exception {
    UUID workspaceId = UUID.randomUUID();
    UUID otherWorkspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    workspaces.create(otherWorkspaceId);
    byte[] docx =
        docx(
            """
            Software Engineer interview requirements
            Job description
            We are looking for a Java developer to join the team.
            Key responsibilities include designing services and reviewing code.
            The candidate must complete screening questions before the interview.
            """);

    OnboardingProfile draft =
        onboardingService.upload(
            workspaceId,
            new MockMultipartFile(
                "file",
                "review-required.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx));

    ResumeReadinessAssessment assessment = onboardingService.readiness(workspaceId);
    assertThat(draft.status()).isEqualTo("DRAFT");
    assertThat(assessment.status()).isEqualTo("REVIEW_REQUIRED");
    assertThat(assessment.acknowledgementRequired()).isTrue();
    assertThatThrownBy(() -> onboardingService.readiness(otherWorkspaceId))
        .isInstanceOf(IllegalStateException.class);
    mvc.perform(
            get("/setup").cookie(new Cookie(WorkspaceContext.COOKIE_NAME, workspaceId.toString())))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/app/index.html"));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("/api/candidate-profile/readiness")));

    SearchPreferences preferences =
        new SearchPreferences(
            "Software Engineer",
            "",
            "Singapore",
            "Java",
            "SG | Singapore",
            2,
            "PERMANENT",
            "HYBRID");
    assertThatThrownBy(
            () -> onboardingService.completeSetup(workspaceId, List.of("Java"), preferences))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acknowledge");

    long candidateId =
        onboardingService.completeSetup(workspaceId, List.of("Java"), preferences, true);
    assertThat(candidateId).isPositive();
    assertThat(
            jdbc.queryForObject(
                "SELECT cardinality(target_domains) FROM candidate_profile WHERE id=?",
                Integer.class,
                candidateId))
        .isZero();
    assertThat(onboardingService.readiness(workspaceId).acknowledgedAt()).isNotNull();
  }

  @Test
  void assessesAReadablePdfFixtureWithoutRetainingItsBytes() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    byte[] pdf =
        pdf(
            """
            Morgan Lee
            morgan@example.com
            Professional Summary
            Business analyst improving customer operations.
            Experience
            Senior Business Analyst - Example Company
            2021 - Present
            Skills
            Business Analysis, Project Management
            Education
            Bachelor of Commerce
            """);

    OnboardingProfile draft =
        onboardingService.upload(
            workspaceId,
            new MockMultipartFile("file", "morgan-resume.pdf", "application/pdf", pdf));

    ResumeReadinessAssessment assessment = onboardingService.readiness(workspaceId);
    assertThat(draft.status()).isEqualTo("DRAFT");
    assertThat(assessment.contentType()).isEqualTo("application/pdf");
    assertThat(assessment.status()).isEqualTo("READY");
    assertThat(
            jdbc.queryForObject(
                "SELECT size_bytes=? FROM workspace_resume WHERE id=(SELECT resume_id FROM workspace_profile_version WHERE id=?)",
                Boolean.class,
                pdf.length,
                draft.id()))
        .isTrue();
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
                Duration.ZERO),
            profileIntelligence,
            new ResumeReadinessRepository(new NamedParameterJdbcTemplate(jdbc)));

    createAndConfirm(configuredOnboarding, workspaceId, "Java Developer", "banking", "Java");

    assertThat(
            jdbc.queryForList(
                "SELECT source FROM search_profile WHERE workspace_id=? AND active=true ORDER BY source",
                String.class,
                workspaceId))
        .containsExactly("ADZUNA", "JOOBLE");

    long indiaResumeId =
        configuredOnboarding.saveResume(
            workspaceId, "india-resume.pdf", "application/pdf", 100, "f".repeat(64));
    long indiaProfileId =
        configuredOnboarding.createDraft(workspaceId, indiaResumeId, "India candidate");
    configuredOnboarding.addSkills(indiaProfileId, List.of("Java"));
    configuredOnboarding.savePreferences(
        workspaceId,
        indiaProfileId,
        new SearchPreferences(
            "Java Developer",
            "banking",
            "Bengaluru",
            "Java",
            "IN | Bengaluru",
            2,
            "PERMANENT",
            "HYBRID"));
    configuredOnboarding.confirm(
        workspaceId, configuredOnboarding.latestProfile(workspaceId).orElseThrow());

    assertThat(
            jdbc.queryForList(
                "SELECT source || '|' || source_key FROM search_profile WHERE workspace_id=? AND active=true ORDER BY source",
                String.class,
                workspaceId))
        .containsExactly("ADZUNA|in");
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

    SearchPreferences indiaOnly =
        new SearchPreferences(
            "Java Developer",
            "banking",
            "Bengaluru",
            "Java Spring",
            "IN | Bengaluru",
            2,
            "PERMANENT",
            "HYBRID");
    onboardingService.savePreferences(workspaceId, indiaOnly);
    onboardingService.confirm(workspaceId);

    assertThat(
            jdbc.queryForList(
                "SELECT source_key || '|' || location FROM search_profile WHERE workspace_id=? AND source='ADZUNA' AND active=true",
                String.class,
                workspaceId))
        .containsExactly("in|Bengaluru");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM search_profile WHERE workspace_id=? AND source='ADZUNA' AND active=false",
                Integer.class,
                workspaceId))
        .isEqualTo(3);
  }

  @Test
  void normalizesSectorsAndPersistsACountryWideMarketWithoutLeakingPrivateValues() {
    UUID workspaceId = UUID.randomUUID();
    UUID otherWorkspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    workspaces.create(otherWorkspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "9".repeat(64));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Country-wide candidate");
    onboarding.addSkills(profileId, List.of("Java"));
    SearchPreferences preferences =
        new SearchPreferences(
            "Backend Engineer",
            "banking, Climate Technology",
            "Melbourne",
            "",
            "AU | ",
            2,
            "PERMANENT",
            "HYBRID");

    onboarding.savePreferences(workspaceId, profileId, preferences);
    onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());

    OnboardingProfile active = onboarding.latestProfile(workspaceId).orElseThrow();
    assertThat(active.targetDomains()).containsExactly("Financial Services", "Climate Technology");
    assertThat(onboarding.preferences(workspaceId).orElseThrow().targets())
        .containsExactly(new SearchTarget("AU", ""));
    assertThat(
            jdbc.queryForObject(
                "SELECT location = '' FROM search_profile WHERE workspace_id=? AND active=true",
                Boolean.class,
                workspaceId))
        .isTrue();
    assertThat(profileIntelligence.sectorOptions(workspaceId, "bank"))
        .extracting(SectorOption::name)
        .containsExactly("Financial Services");
    assertThat(profileIntelligence.roleOptions(workspaceId, "Front-end Developer"))
        .extracting(RoleOption::name)
        .contains("Frontend Engineer");
    assertThat(profileIntelligence.sectorOptions(workspaceId, "Climate Technology"))
        .singleElement()
        .satisfies(
            option -> {
              assertThat(option.custom()).isTrue();
              assertThat(option.taxonomyVersion()).isNull();
            });
    assertThat(profileIntelligence.sectorOptions(otherWorkspaceId, "Climate Technology")).isEmpty();
  }

  @Test
  void preservesEscoRoleButUsesGeneralizedProviderKeywords() {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "e".repeat(64));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Account manager");
    onboarding.addSkills(profileId, List.of("Account management"));
    onboarding.savePreferences(
        workspaceId,
        profileId,
        new SearchPreferences(
            "ICT account manager",
            "technology",
            "India",
            "ICT account manager",
            "IN | India",
            2,
            "PERMANENT",
            "HYBRID"));
    onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());

    assertThat(onboarding.latestProfile(workspaceId).orElseThrow().targetRoles())
        .containsExactly("ICT account manager");
    assertThat(onboarding.preferences(workspaceId).orElseThrow().keywords())
        .isEqualTo("account manager");
    assertThat(
            jdbc.queryForObject(
                "SELECT keywords FROM search_profile WHERE workspace_id=? AND active=true",
                String.class,
                workspaceId))
        .isEqualTo("account manager");
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

  @Test
  void storesOrderedRoleIntentAndGeneratesInspectableQueriesWhenOverrideIsBlank() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    workspaces.create(workspaceId);
    long resumeId =
        onboarding.saveResume(workspaceId, "resume.pdf", "application/pdf", 100, "9".repeat(64));
    long profileId = onboarding.createDraft(workspaceId, resumeId, "Multiple directions");
    onboarding.addSkills(profileId, List.of("React", "TypeScript", "Machine Learning", "CRM"));

    onboarding.savePreferences(
        workspaceId,
        profileId,
        new SearchPreferences(
            "Frontend Engineer, Data Scientist, Sales Manager",
            "",
            "Singapore",
            "",
            "SG | Singapore",
            2,
            "PERMANENT",
            "HYBRID"));
    long candidateId =
        onboarding.confirm(workspaceId, onboarding.latestProfile(workspaceId).orElseThrow());

    assertThat(onboarding.preferences(workspaceId).orElseThrow().keywords()).isBlank();
    assertThat(
            jdbc.queryForList(
                "SELECT role.canonical_name FROM workspace_profile_target_role target JOIN role_catalog role ON role.id=target.role_id WHERE target.profile_version_id=? ORDER BY target.priority",
                String.class,
                profileId))
        .containsExactly("Frontend Engineer", "Data Scientist", "Sales Manager");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM candidate_target_role WHERE candidate_profile_id=?",
                Integer.class,
                candidateId))
        .isEqualTo(3);
    assertThat(onboarding.providerQueries(workspaceId))
        .extracting(ProviderQueryPreview::query)
        .containsExactly(
            "Frontend Engineer React TypeScript",
            "Data Scientist Machine Learning",
            "Sales Manager CRM");
    assertThat(
            jdbc.queryForList(
                "SELECT keywords FROM search_profile WHERE workspace_id=? AND active=true ORDER BY search_query_id",
                String.class,
                workspaceId))
        .containsExactly(
            "Frontend Engineer React TypeScript",
            "Data Scientist Machine Learning",
            "Sales Manager CRM");

    mvc.perform(
            get("/api/candidate-profile/search-queries")
                .cookie(new Cookie(WorkspaceContext.COOKIE_NAME, workspaceId.toString())))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("role-intent-v1")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString("Frontend Engineer React TypeScript")));
    mvc.perform(
            get("/setup").cookie(new Cookie(WorkspaceContext.COOKIE_NAME, workspaceId.toString())))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/app/index.html"));
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

  private static byte[] docx(String text) throws Exception {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      writeZipEntry(
          zip,
          "[Content_Types].xml",
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          </Types>
          """);
      writeZipEntry(
          zip,
          "_rels/.rels",
          """
          <?xml version="1.0" encoding="UTF-8"?>
          <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
          </Relationships>
          """);
      String paragraphs =
          text.lines()
              .map(
                  line ->
                      "<w:p><w:r><w:t>"
                          + line.replace("&", "&amp;").replace("<", "&lt;")
                          + "</w:t></w:r></w:p>")
              .collect(java.util.stream.Collectors.joining());
      writeZipEntry(
          zip,
          "word/document.xml",
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
              + paragraphs
              + "</w:body></w:document>");
    }
    return output.toByteArray();
  }

  private static byte[] pdf(String text) throws Exception {
    var output = new ByteArrayOutputStream();
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      try (var stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        stream.newLineAtOffset(50, 750);
        for (String line : text.lines().toList()) {
          stream.showText(line.replace('—', '-'));
          stream.newLineAtOffset(0, -15);
        }
        stream.endText();
      }
      document.save(output);
    }
    return output.toByteArray();
  }

  private static void writeZipEntry(ZipOutputStream zip, String name, String value)
      throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
