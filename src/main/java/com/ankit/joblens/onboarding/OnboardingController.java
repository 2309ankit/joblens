package com.ankit.joblens.onboarding;

import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OnboardingController {
  private final WorkspaceContext workspaceContext;
  private final OnboardingService onboardingService;

  public OnboardingController(
      WorkspaceContext workspaceContext, OnboardingService onboardingService) {
    this.workspaceContext = workspaceContext;
    this.onboardingService = onboardingService;
  }

  @GetMapping("/setup")
  public String setup(HttpServletRequest request, HttpServletResponse response, Model model) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    model.addAttribute("workspaceId", workspaceId);
    OnboardingProfile profile = onboardingService.latest(workspaceId).orElse(null);
    model.addAttribute("profile", profile);
    model.addAttribute("skillCatalog", onboardingService.skillOptions(workspaceId, ""));
    model.addAttribute("roleCatalog", onboardingService.roleOptions(workspaceId, ""));
    List<IntegratedCountry> countries = onboardingService.countries();
    model.addAttribute("countries", countries);
    model.addAttribute(
        "integratedCountryCodes", countries.stream().map(IntegratedCountry::code).toList());
    model.addAttribute(
        "profileIntelligence",
        profile == null
            ? new ProfileIntelligence(List.of(), List.of())
            : onboardingService.intelligence(workspaceId));
    ResumeReadinessAssessment readiness = null;
    if (profile != null) {
      try {
        readiness = onboardingService.readiness(workspaceId);
      } catch (IllegalStateException legacyProfileWithoutAssessment) {
        // Profiles created before the advisor milestone have no retained resume text to reassess.
      }
    }
    model.addAttribute("readiness", readiness);
    if (!model.containsAttribute("preferences")) {
      model.addAttribute(
          "preferences",
          onboardingService
              .preferences(workspaceId)
              .orElseGet(
                  () ->
                      new SearchPreferences(
                          "",
                          "",
                          "",
                          "",
                          "SG | Singapore",
                          3,
                          "PERMANENT",
                          "REMOTE,HYBRID,ONSITE")));
    }
    SearchPreferences preferences = (SearchPreferences) model.getAttribute("preferences");
    model.addAttribute("searchTargets", preferences.targets());
    return "setup";
  }

  @PostMapping("/setup/complete")
  public String complete(
      @RequestParam(name = "skills", required = false) List<String> skills,
      @RequestParam(name = "targetRoles", required = false) List<String> targetRoles,
      @RequestParam String targetDomains,
      @RequestParam String primaryLocation,
      @RequestParam String keywords,
      @RequestParam List<String> countryCodes,
      @RequestParam List<String> locations,
      @RequestParam int maxPages,
      @RequestParam String employmentPreference,
      @RequestParam String workPreference,
      @RequestParam(name = "acknowledgeReadiness", defaultValue = "false")
          boolean acknowledgeReadiness,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      if (countryCodes.size() != locations.size()) {
        throw new IllegalArgumentException("Every country needs one search location");
      }
      List<SearchTarget> targets =
          java.util.stream.IntStream.range(0, countryCodes.size())
              .mapToObj(index -> new SearchTarget(countryCodes.get(index), locations.get(index)))
              .toList();
      SearchPreferences preferences =
          new SearchPreferences(
              targetRoles == null ? "" : String.join(", ", targetRoles),
              targetDomains,
              primaryLocation,
              keywords,
              SearchTarget.format(targets),
              maxPages,
              employmentPreference,
              workPreference);
      long candidateProfileId =
          onboardingService.completeSetup(workspaceId, skills, preferences, acknowledgeReadiness);
      redirectAttributes.addFlashAttribute(
          "message", "Profile activated. Candidate profile " + candidateProfileId + " is ready.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/setup";
  }

  @PostMapping("/setup/skills")
  public String skills(
      @RequestParam(name = "skills", required = false) List<String> skills,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      OnboardingProfile profile = onboardingService.updateSkills(workspaceId, skills);
      redirectAttributes.addFlashAttribute(
          "message", "Skills saved in profile draft " + profile.version() + ".");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/setup";
  }

  @PostMapping("/setup/resume")
  public String upload(
      @RequestParam("file") MultipartFile file,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      OnboardingProfile profile = onboardingService.upload(workspaceId, file);
      redirectAttributes.addFlashAttribute(
          "message",
          "Resume read. Found "
              + profile.skills().size()
              + " skill suggestions and a machine-readability assessment; review them before activation.");
    } catch (Exception exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/setup";
  }

  @PostMapping("/setup/preferences")
  public String preferences(
      @Valid @ModelAttribute("preferences") SearchPreferences preferences,
      BindingResult bindingResult,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute("error", "Complete all required preferences");
      return "redirect:/setup";
    }
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      onboardingService.savePreferences(workspaceId, preferences);
      redirectAttributes.addFlashAttribute("message", "Preferences saved. Confirm your profile.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/setup";
  }

  @PostMapping("/setup/confirm")
  public String confirm(
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      long candidateProfileId = onboardingService.confirm(workspaceId);
      redirectAttributes.addFlashAttribute(
          "message", "Profile confirmed. Candidate profile " + candidateProfileId + " is ready.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/setup";
  }
}
