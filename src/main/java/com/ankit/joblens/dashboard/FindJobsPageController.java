package com.ankit.joblens.dashboard;

import com.ankit.joblens.discovery.FindJobsService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FindJobsPageController {
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;
  private final FindJobsService service;

  public FindJobsPageController(
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles,
      FindJobsService service) {
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
    this.service = service;
  }

  @PostMapping("/find-jobs")
  public String run(
      @RequestParam(required = false) LocalDate businessDate,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      var execution =
          service.run(
              workspaceId,
              candidateProfiles.requireCandidateProfile(workspaceId),
              businessDate == null ? LocalDate.now() : businessDate);
      redirectAttributes.addFlashAttribute(
          "message",
          "Find jobs finished with " + execution.getStatus() + ". Rankings are refreshed.");
    } catch (JobExecutionException | RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "error", "Find Jobs could not start. Please check your search status and try again.");
    }
    return "redirect:/dashboard";
  }

  @PostMapping("/find-jobs/runs/{runId}/restart")
  public String restart(
      @org.springframework.web.bind.annotation.PathVariable long runId,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirectAttributes) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      var execution = service.restartByRunId(workspaceId, runId);
      redirectAttributes.addFlashAttribute(
          "message", "Find jobs restart finished with " + execution.getStatus() + ".");
    } catch (JobExecutionException | RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "error",
          "That search cannot be restarted right now. Please refresh its status and try again.");
    }
    return "redirect:/dashboard";
  }
}
