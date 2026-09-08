package com.ankit.joblens.dashboard;

import com.ankit.joblens.lifecycle.ApplicationLifecycleService;
import com.ankit.joblens.lifecycle.ApplicationStatus;
import com.ankit.joblens.lifecycle.FollowUpJobService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
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
  private final FollowUpJobService followUpJob;

  public ApplicationPageController(
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles,
      ApplicationLifecycleService lifecycle,
      FollowUpJobService followUpJob) {
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
    this.lifecycle = lifecycle;
    this.followUpJob = followUpJob;
  }

  @GetMapping("/applications")
  public String applications(
      Model model, HttpServletRequest request, HttpServletResponse response) {
    try {
      owner(request, response);
    } catch (IllegalStateException exception) {
      return "redirect:/setup";
    }
    return "forward:/app/index.html";
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

  private WorkspaceCandidate owner(HttpServletRequest request, HttpServletResponse response) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    return new WorkspaceCandidate(
        workspaceId, candidateProfiles.requireCandidateProfile(workspaceId));
  }

  private record WorkspaceCandidate(UUID workspaceId, long candidateProfileId) {}
}
