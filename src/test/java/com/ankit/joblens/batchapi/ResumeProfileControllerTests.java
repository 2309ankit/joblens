package com.ankit.joblens.batchapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ankit.joblens.onboarding.IntegratedCountry;
import com.ankit.joblens.onboarding.OnboardingProfile;
import com.ankit.joblens.onboarding.OnboardingService;
import com.ankit.joblens.onboarding.ProfileIntelligence;
import com.ankit.joblens.onboarding.ResumeReadinessAssessment;
import com.ankit.joblens.onboarding.RoleOption;
import com.ankit.joblens.onboarding.SearchPreferences;
import com.ankit.joblens.onboarding.SearchTarget;
import com.ankit.joblens.onboarding.SectorOption;
import com.ankit.joblens.onboarding.SkillOption;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ResumeProfileControllerTests {
  private UUID workspaceId;
  private StubOnboardingService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    workspaceId = UUID.randomUUID();
    WorkspaceContext workspaceContext =
        new WorkspaceContext(null) {
          @Override
          public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
            return workspaceId;
          }
        };
    service = new StubOnboardingService();
    mvc =
        MockMvcBuilders.standaloneSetup(new ResumeProfileController(workspaceContext, service))
            .setControllerAdvice(new CandidateProfileExceptionHandler())
            .build();
  }

  @Test
  void savesReviewedSkillsForTheResolvedWorkspace() throws Exception {
    mvc.perform(
            put("/api/candidate-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":[\"Java\",\"Kafka\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.skills[0]").value("Java"))
        .andExpect(jsonPath("$.skills[1]").value("Kafka"));
    assertThat(service.lastWorkspaceId).isEqualTo(workspaceId);
  }

  @Test
  void reportsInvalidSkillSelectionsWithoutAStackTraceResponse() throws Exception {
    service.rejectUpdate = true;

    mvc.perform(
            put("/api/candidate-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":[\"Invalid control value\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PROFILE_REQUEST"))
        .andExpect(
            jsonPath("$.message").value("Each skill must be plain text up to 100 characters"));
  }

  @Test
  void exposesSearchableTaxonomySuggestionsAndCountryCapabilities() throws Exception {
    mvc.perform(get("/api/candidate-profile/skills/catalog").param("query", "react"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("React"))
        .andExpect(jsonPath("$[0].category").value("FRONTEND"));
    mvc.perform(get("/api/candidate-profile/roles/catalog").param("query", "product"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Product Manager"));
    mvc.perform(get("/api/candidate-profile/sectors/catalog").param("query", "bank"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Financial Services"))
        .andExpect(jsonPath("$[0].taxonomyVersion").value("joblens-sector-v1"));
    mvc.perform(get("/api/candidate-profile/countries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("SG"))
        .andExpect(jsonPath("$[0].name").value("Singapore"))
        .andExpect(jsonPath("$[0].sources[0]").value("ADZUNA"));
    mvc.perform(get("/api/candidate-profile/intelligence"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleSuggestions[0].name").value("Product Manager"))
        .andExpect(jsonPath("$.roleSuggestions[0].evidenceSource").value("RESUME_HEADLINE"));
    mvc.perform(get("/api/candidate-profile/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assessmentVersion").value("readability-v1"))
        .andExpect(jsonPath("$.score").value(70))
        .andExpect(jsonPath("$.findings[0].code").value("DOCUMENT_TYPE_UNCERTAIN"));
    mvc.perform(post("/api/candidate-profile/readiness/acknowledgement"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acknowledgedAt").value("2026-09-01T12:00:00Z"));
    assertThat(service.lastWorkspaceId).isEqualTo(workspaceId);
  }

  @Test
  void activatesEveryReviewedSearchMarketAndRejectsDuplicates() throws Exception {
    String request =
        """
        {
          "skills": ["Java"],
          "targetRoles": ["Backend Engineer"],
          "targetDomains": "banking",
          "primaryLocation": "Singapore",
          "keywords": "",
          "searchMarkets": [
            {"countryCode": "SG", "location": "Singapore"},
            {"countryCode": "AU", "location": "Sydney"}
          ],
          "maxPages": 3,
          "employmentPreference": "PERMANENT",
          "workPreference": "HYBRID",
          "acknowledgeReadiness": false
        }
        """;

    mvc.perform(
            post("/api/candidate-profile/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk());

    assertThat(service.lastPreferences.targets())
        .containsExactly(new SearchTarget("SG", "Singapore"), new SearchTarget("AU", "Sydney"));

    mvc.perform(
            post("/api/candidate-profile/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request.replace(
                        "{\"countryCode\": \"AU\", \"location\": \"Sydney\"}",
                        "{\"countryCode\": \"sg\", \"location\": \"singapore\"}")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PROFILE_REQUEST"))
        .andExpect(
            jsonPath("$.message").value("Remove duplicate search markets before activation"));

    mvc.perform(
            post("/api/candidate-profile/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request.replace(
                        "{\"countryCode\": \"AU\", \"location\": \"Sydney\"}",
                        "{\"countryCode\": \"AU\", \"location\": \"\"}")))
        .andExpect(status().isOk());
    assertThat(service.lastPreferences.targets().get(1)).isEqualTo(new SearchTarget("AU", ""));
  }

  private static final class StubOnboardingService extends OnboardingService {
    private UUID lastWorkspaceId;
    private SearchPreferences lastPreferences;
    private boolean rejectUpdate;

    private StubOnboardingService() {
      super(null, null, null, null);
    }

    @Override
    public OnboardingProfile updateSkills(UUID workspaceId, List<String> skills) {
      lastWorkspaceId = workspaceId;
      if (rejectUpdate) {
        throw new IllegalArgumentException("Each skill must be plain text up to 100 characters");
      }
      return new OnboardingProfile(
          12,
          2,
          "DRAFT",
          "Candidate",
          List.of("Backend Engineer"),
          List.of("banking"),
          "Singapore",
          skills);
    }

    @Override
    public List<SkillOption> skillOptions(UUID workspaceId, String query) {
      lastWorkspaceId = workspaceId;
      return List.of(new SkillOption("React", "FRONTEND", false));
    }

    @Override
    public List<RoleOption> roleOptions(UUID workspaceId, String query) {
      lastWorkspaceId = workspaceId;
      return List.of(new RoleOption("Product Manager", "PRODUCT", false));
    }

    @Override
    public List<SectorOption> sectorOptions(UUID workspaceId, String query) {
      lastWorkspaceId = workspaceId;
      return List.of(
          new SectorOption("Financial Services", "BUSINESS", false, "joblens-sector-v1"));
    }

    @Override
    public List<IntegratedCountry> countries() {
      return List.of(
          new IntegratedCountry(
              "SG", "Singapore", List.of("ADZUNA"), "Integrated search available through ADZUNA."));
    }

    @Override
    public ProfileIntelligence intelligence(UUID workspaceId) {
      lastWorkspaceId = workspaceId;
      return new ProfileIntelligence(
          List.of(),
          List.of(
              new ProfileIntelligence.RoleSuggestion(
                  "Product Manager",
                  "PRODUCT",
                  "RESUME_HEADLINE",
                  "Senior Product Manager",
                  new BigDecimal("0.950"))));
    }

    @Override
    public ResumeReadinessAssessment readiness(UUID workspaceId) {
      lastWorkspaceId = workspaceId;
      return assessment(null);
    }

    @Override
    public ResumeReadinessAssessment acknowledgeReadiness(UUID workspaceId) {
      lastWorkspaceId = workspaceId;
      return assessment(OffsetDateTime.parse("2026-09-01T12:00:00Z"));
    }

    private static ResumeReadinessAssessment assessment(OffsetDateTime acknowledgedAt) {
      return new ResumeReadinessAssessment(
          12,
          "readability-v1",
          "REVIEW_REQUIRED",
          70,
          "application/pdf",
          200,
          30,
          acknowledgedAt,
          OffsetDateTime.parse("2026-09-01T11:00:00Z"),
          List.of(
              new ResumeReadinessAssessment.Finding(
                  "DOCUMENT_TYPE_UNCERTAIN",
                  "DOCUMENT_TYPE",
                  "REVIEW",
                  "Review the document type.",
                  "Confirm this is your resume.",
                  "Requirement signals: 3",
                  30)));
    }

    @Override
    public long completeSetup(
        UUID workspaceId,
        List<String> requestedSkills,
        SearchPreferences preferences,
        boolean acknowledgeReadiness) {
      lastWorkspaceId = workspaceId;
      preferences.targets();
      lastPreferences = preferences;
      return 42;
    }

    @Override
    public Optional<OnboardingProfile> latest(UUID workspaceId) {
      lastWorkspaceId = workspaceId;
      return Optional.of(
          new OnboardingProfile(
              12,
              2,
              "ACTIVE",
              "Candidate",
              List.of("Backend Engineer"),
              List.of("banking"),
              "Singapore",
              List.of("Java")));
    }
  }
}
