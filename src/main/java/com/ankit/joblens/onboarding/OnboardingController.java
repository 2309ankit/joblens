package com.ankit.joblens.onboarding;

import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
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
  public String setup(HttpServletRequest request, HttpServletResponse response) {
    workspaceContext.resolve(request, response);
    return "forward:/app/index.html";
  }

  @PostMapping("/setup/complete")
  public String complete(
      @RequestParam(name = "skills", required = false) List<String> skills,
      @RequestParam(name = "targetRoles", required = false) List<String> targetRoles,
      @RequestParam String targetDomains,
      @RequestParam String primaryLocation,
      @RequestParam(name = "keywords", defaultValue = "") String keywords,
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
      onboardingService.completeSetup(workspaceId, skills, preferences, acknowledgeReadiness);
      redirectAttributes.addFlashAttribute(
          "message", "Profile activated. Your generated searches are ready.");
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
      onboardingService.confirm(workspaceId);
      redirectAttributes.addFlashAttribute("message", "Profile confirmed and ready.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/setup";
  }
}
