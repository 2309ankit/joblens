package com.ankit.joblens.dashboard;

import com.ankit.joblens.lifecycle.ApplicationLifecyclePolicy;
import com.ankit.joblens.lifecycle.ApplicationLifecycleService;
import com.ankit.joblens.lifecycle.ApplicationQueryRepository;
import com.ankit.joblens.lifecycle.ApplicationStatus;
import com.ankit.joblens.lifecycle.FollowUpJobService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ApplicationPageController {
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;
  private final ApplicationLifecycleService lifecycle;
  private final ApplicationLifecyclePolicy policy;
  private final ApplicationQueryRepository queries;
  private final FollowUpJobService followUpJob;

  public ApplicationPageController(
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles,
      ApplicationLifecycleService lifecycle,
      ApplicationLifecyclePolicy policy,
      ApplicationQueryRepository queries,
      FollowUpJobService followUpJob) {
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
    this.lifecycle = lifecycle;
    this.policy = policy;
    this.queries = queries;
    this.followUpJob = followUpJob;
  }

  @GetMapping("/applications")
  public String applications(
      Model model, HttpServletRequest request, HttpServletResponse response) {
    WorkspaceCandidate owner;
    try {
      owner = owner(request, response);
    } catch (IllegalStateException exception) {
      return "redirect:/setup";
    }
    List<Map<String, Object>> applications =
        queries.findApplications(null, owner.candidateProfileId()).stream()
            .map(this::withAllowedTransitions)
            .toList();
    model.addAttribute("applications", applications);
    model.addAttribute("followUps", queries.findFollowUps(null, null, owner.candidateProfileId()));
    model.addAttribute("today", LocalDate.now());
    return "applications";
  }

  @PostMapping("/applications/save")
  public String save(
      @RequestParam long jobId,
      @RequestParam(required = false) String note,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    WorkspaceCandidate owner = owner(request, response);
    try {
      lifecycle.create(
          jobId, owner.candidateProfileId(), owner.workspaceId(), LocalDate.now(), note);
      redirectAttributes.addFlashAttribute("message", "Job saved to applications.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/applications";
  }

  @PostMapping("/applications/{id}/transition")
  public String transition(
      @PathVariable long id,
      @RequestParam ApplicationStatus status,
      @RequestParam LocalDate effectiveDate,
      @RequestParam(required = false) String note,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    WorkspaceCandidate owner = owner(request, response);
    try {
      lifecycle.transition(id, owner.candidateProfileId(), status, effectiveDate, note);
      redirectAttributes.addFlashAttribute(
          "message", "Application status updated to " + status + ".");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/applications";
  }

  @PostMapping("/applications/follow-ups/run")
  public String refreshFollowUps(
      @RequestParam LocalDate businessDate,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    WorkspaceCandidate owner = owner(request, response);
    try {
      var execution = followUpJob.run(businessDate, owner.candidateProfileId(), null);
      redirectAttributes.addFlashAttribute(
          "message", "Follow-up batch finished with status " + execution.getStatus() + ".");
    } catch (org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException exception) {
      redirectAttributes.addFlashAttribute("message", "Follow-ups are already up to date.");
    } catch (JobExecutionException | RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/applications";
  }

  @PostMapping("/applications/follow-ups/{id}/complete")
  public String completeFollowUp(
      @PathVariable long id,
      @RequestParam LocalDate completedOn,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    WorkspaceCandidate owner = owner(request, response);
    try {
      lifecycle.completeFollowUp(id, owner.candidateProfileId(), completedOn);
      redirectAttributes.addFlashAttribute("message", "Follow-up completed.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/applications";
  }

  private Map<String, Object> withAllowedTransitions(Map<String, Object> application) {
    var result = new LinkedHashMap<>(application);
    ApplicationStatus current = ApplicationStatus.valueOf((String) application.get("status"));
    result.put(
        "allowedTransitions",
        policy.allowedTransitions(current).stream().map(ApplicationStatus::name).toList());
    return result;
  }

  private WorkspaceCandidate owner(HttpServletRequest request, HttpServletResponse response) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    return new WorkspaceCandidate(
        workspaceId, candidateProfiles.requireCandidateProfile(workspaceId));
  }

  private record WorkspaceCandidate(UUID workspaceId, long candidateProfileId) {}
}
