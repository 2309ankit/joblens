package com.ankit.joblens.batchapi;

import com.ankit.joblens.lifecycle.FollowUpJobService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/batch/follow-ups")
@Tag(name = "Follow-ups", description = "Generate candidate-owned application reminders")
public class ApplicationFollowUpJobController {

  private final FollowUpJobService service;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public ApplicationFollowUpJobController(
      FollowUpJobService service,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.service = service;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @PostMapping("/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "Refresh application follow-ups",
      description =
          "Runs the restartable follow-up batch for this browser workspace's confirmed candidate. A changed application lifecycle creates a new logical JobInstance; an unchanged rerun is rejected as already complete.")
  public JobLaunchResponse run(
      @RequestParam LocalDate businessDate,
      @RequestParam(required = false) Long failAfterApplications,
      HttpServletRequest request,
      HttpServletResponse response)
      throws JobExecutionException {
    if (failAfterApplications != null && failAfterApplications < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "failAfterApplications must be positive");
    }
    long candidateProfileId =
        candidateProfiles.requireCandidateProfile(workspaceContext.resolve(request, response));
    JobExecution execution = service.run(businessDate, candidateProfileId, failAfterApplications);
    return BatchResponses.from(execution);
  }
}
