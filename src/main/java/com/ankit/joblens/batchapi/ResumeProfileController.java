package com.ankit.joblens.batchapi;

import com.ankit.joblens.onboarding.OnboardingProfile;
import com.ankit.joblens.onboarding.OnboardingService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
      summary = "List supported skills",
      description = "Returns the canonical skill names accepted by the resume skill editor.")
  public List<String> skillCatalog() {
    return service.skillCatalog();
  }

  @PutMapping
  @Operation(
      summary = "Save reviewed skills",
      description =
          "Replaces the latest draft's skills with canonical catalog values. Editing an active profile first creates a new draft; scoring changes only after confirmation on /setup.")
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
          "Validates a PDF, DOC, or DOCX up to 5 MB, extracts readable text, detects catalog skills, and creates a versioned profile draft for this browser workspace. Original file bytes are not retained.")
  public OnboardingProfile upload(
      @RequestPart("file") MultipartFile file,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {
    return service.upload(workspaceContext.resolve(request, response), file);
  }

  public record SkillsRequest(@NotEmpty List<@NotBlank String> skills) {}
}
