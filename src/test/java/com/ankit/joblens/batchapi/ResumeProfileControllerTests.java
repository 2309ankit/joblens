package com.ankit.joblens.batchapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ankit.joblens.onboarding.OnboardingProfile;
import com.ankit.joblens.onboarding.OnboardingService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
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
                .content("{\"skills\":[\"Imaginary Skill\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PROFILE_REQUEST"))
        .andExpect(jsonPath("$.message").value("Choose skills from the supported catalog"));
  }

  private static final class StubOnboardingService extends OnboardingService {
    private UUID lastWorkspaceId;
    private boolean rejectUpdate;

    private StubOnboardingService() {
      super(null);
    }

    @Override
    public OnboardingProfile updateSkills(UUID workspaceId, List<String> skills) {
      lastWorkspaceId = workspaceId;
      if (rejectUpdate) {
        throw new IllegalArgumentException("Choose skills from the supported catalog");
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
  }
}
