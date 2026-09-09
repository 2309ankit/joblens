package com.ankit.joblens.batchapi;

import com.ankit.joblens.onboarding.IntegratedCountry;
import com.ankit.joblens.onboarding.OnboardingProfile;
import com.ankit.joblens.onboarding.OnboardingService;
import com.ankit.joblens.onboarding.ProfileIntelligence;
import com.ankit.joblens.onboarding.ProviderQueryPreview;
import com.ankit.joblens.onboarding.ResumeReadinessAssessment;
import com.ankit.joblens.onboarding.RoleOption;
import com.ankit.joblens.onboarding.SearchPreferences;
import com.ankit.joblens.onboarding.SearchTarget;
import com.ankit.joblens.onboarding.SkillOption;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/candidate-profile")
@Tag(
    name = "Candidate profile",
    description =
        "Upload a resume and review the skills for this anonymous browser workspace before profile confirmation")
public class ResumeProfileController {
  private final WorkspaceContext workspaceContext;
  private final OnboardingService service;

  public ResumeProfileController(WorkspaceContext workspaceContext, OnboardingService service) {
    this.workspaceContext = workspaceContext;
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Get current candidate profile",
      description =
          "Returns this browser workspace's latest profile version. DRAFT skills are not used for scoring until the profile is confirmed.")
  public OnboardingProfile profile(HttpServletRequest request, HttpServletResponse response) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    return service
        .latest(workspaceId)
        .orElseThrow(() -> new IllegalStateException("Upload a valid resume first"));
  }

  @GetMapping("/skills/catalog")
  @Operation(
      summary = "Search the skill catalogue",
      description =
          "Returns categorized starter skills and workspace-private additions. The optional query performs a case-insensitive name search.")
  public List<SkillOption> skillCatalog(
      @RequestParam(defaultValue = "") String query,
      HttpServletRequest request,
      HttpServletResponse response) {
    return service.skillOptions(workspaceContext.resolve(request, response), query);
  }

  @GetMapping("/roles/catalog")
  @Operation(
      summary = "Search the role catalogue",
      description =
          "Returns categorized starter roles and workspace-private additions for the creatable role editor.")
  public List<RoleOption> roleCatalog(
      @RequestParam(defaultValue = "") String query,
      HttpServletRequest request,
      HttpServletResponse response) {
    return service.roleOptions(workspaceContext.resolve(request, response), query);
  }

  @GetMapping("/countries")
  @Operation(
      summary = "List integrated search countries",
      description =
          "Returns country names with internal ISO alpha-2 codes, supporting providers, and a capability explanation. Unsupported countries cannot be activated.")
  public List<IntegratedCountry> countries() {
    return service.countries();
  }

  @GetMapping("/intelligence")
  @Operation(
      summary = "Inspect profile suggestions",
      description =
          "Returns versioned deterministic skill and title suggestions with matched evidence, source, category, and confidence. Suggestions remain user-reviewable and are not claims of fact.")
  public ProfileIntelligence intelligence(
      HttpServletRequest request, HttpServletResponse response) {
    return service.intelligence(workspaceContext.resolve(request, response));
  }

  @GetMapping("/readiness")
  @Operation(
      summary = "Inspect resume machine-readability guidance",
      description =
          "Returns a versioned, deterministic assessment with stable findings, bounded evidence, remediation, and a score that measures parser readability rather than candidate quality or employability.")
  public ResumeReadinessAssessment readiness(
      HttpServletRequest request, HttpServletResponse response) {
    return service.readiness(workspaceContext.resolve(request, response));
  }

  @PostMapping("/readiness/acknowledgement")
  @Operation(
      summary = "Acknowledge review-required readability findings",
      description =
          "Acknowledges the current draft's review warning without claiming the document is ATS-compatible. Required only when measurable evidence makes the document type or parsing quality uncertain.")
  public ResumeReadinessAssessment acknowledgeReadiness(
      HttpServletRequest request, HttpServletResponse response) {
    return service.acknowledgeReadiness(workspaceContext.resolve(request, response));
  }

  @GetMapping("/preferences")
  @Operation(
      summary = "Get current search preferences",
      description =
          "Returns the workspace's roles, domains, keywords, and normalized search-market rows. Each market is executed independently after profile confirmation.")
  public SearchPreferenceView preferences(
      HttpServletRequest request, HttpServletResponse response) {
    SearchPreferences preferences =
        service
            .preferences(workspaceContext.resolve(request, response))
            .orElseThrow(() -> new IllegalStateException("Save job preferences first"));
    return new SearchPreferenceView(
        preferences.targetRoles(),
        preferences.targetDomains(),
        preferences.primaryLocation(),
        preferences.keywords(),
        preferences.targets(),
        preferences.maxPages(),
        preferences.employmentPreference(),
        preferences.workPreference());
  }

  @GetMapping("/search-queries")
  @Operation(
      summary = "Inspect generated provider queries",
      description =
          "Returns the reproducible role-intent query plan for each selected market. GENERATED queries come from selected roles and confirmed skills; OVERRIDE means the optional advanced text was used.")
  public List<ProviderQueryPreview> searchQueries(
      HttpServletRequest request, HttpServletResponse response) {
    return service.providerQueries(workspaceContext.resolve(request, response));
  }

  @PutMapping
  @Operation(
      summary = "Save reviewed skills",
      description =
          "Replaces the latest draft's skills with catalogue values or creates workspace-private additions. Editing an active profile first creates a new draft; scoring changes only after confirmation on /setup.")
  public OnboardingProfile update(
      @Valid @RequestBody SkillsRequest skillsRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    return service.updateSkills(
        workspaceContext.resolve(request, response), skillsRequest.skills());
  }

  @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Read a resume",
      description =
          "Reads a PDF, DOC, or DOCX up to 5 MB and creates a versioned draft with deterministic skill, title, and machine-readability findings. Unusual or suspicious readable documents are preserved for acknowledgement instead of rejected. Original file bytes are not retained.")
  public OnboardingProfile upload(
      @RequestPart("file") MultipartFile file,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {
    return service.upload(workspaceContext.resolve(request, response), file);
  }

  @PostMapping("/activate")
  @Operation(
      summary = "Save the reviewed profile and activate it",
      description =
          "Atomically saves visible user-selected skills, target roles, and search preferences for this browser workspace. Resume suggestions remain suggestions until included in this request.")
  public OnboardingProfile activate(
      @Valid @RequestBody ActivationRequest activation,
      HttpServletRequest request,
      HttpServletResponse response) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    SearchPreferences preferences =
        new SearchPreferences(
            String.join(", ", activation.targetRoles()),
            activation.targetDomains(),
            activation.primaryLocation(),
            activation.keywords(),
            SearchTarget.format(activation.searchMarkets()),
            activation.maxPages(),
            activation.employmentPreference(),
            activation.workPreference());
    service.completeSetup(
        workspaceId, activation.skills(), preferences, activation.acknowledgeReadiness());
    return service.latest(workspaceId).orElseThrow();
  }

  public record SkillsRequest(@NotEmpty List<@NotBlank String> skills) {}

  public record ActivationRequest(
      @NotEmpty List<@NotBlank String> skills,
      @NotEmpty List<@NotBlank String> targetRoles,
      String targetDomains,
      @NotBlank String primaryLocation,
      String keywords,
      @NotEmpty(message = "Add at least one search market")
          @Size(max = 10, message = "Choose no more than 10 search markets")
          List<@Valid SearchTarget> searchMarkets,
      @Min(1) @Max(20) int maxPages,
      @NotBlank String employmentPreference,
      @NotBlank String workPreference,
      boolean acknowledgeReadiness) {}

  public record SearchPreferenceView(
      String targetRoles,
      String targetDomains,
      String primaryLocation,
      String keywords,
      List<SearchTarget> searchMarkets,
      int maxPages,
      String employmentPreference,
      String workPreference) {}
}
