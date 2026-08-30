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
    model.addAttribute("profile", onboardingService.latest(workspaceId).orElse(null));
    if (!model.containsAttribute("preferences")) {
      model.addAttribute(
          "preferences",
          new SearchPreferences(
              "Senior Java Developer, Senior Backend Engineer",
              "banking, payments",
              "Singapore",
              "Java Spring Boot",
              "Singapore",
              "sg",
              List.of("ADZUNA"),
              3,
              "PERMANENT",
              "REMOTE,HYBRID,ONSITE"));
    }
    return "setup";
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
          "message", "Resume validated. Found " + profile.skills().size() + " skills.");
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
