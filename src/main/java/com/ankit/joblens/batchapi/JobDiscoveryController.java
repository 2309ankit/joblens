package com.ankit.joblens.batchapi;

import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch/discovery")
public class JobDiscoveryController {

  private final JobOperator jobOperator;
  private final Job jobDiscoveryJob;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public JobDiscoveryController(
      JobOperator jobOperator,
      @Qualifier("jobDiscoveryJob") Job jobDiscoveryJob,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.jobOperator = jobOperator;
    this.jobDiscoveryJob = jobDiscoveryJob;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @PostMapping("/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public JobLaunchResponse run(
      @RequestParam LocalDate businessDate,
      @RequestParam(required = false) String profileId,
      HttpServletRequest request,
      HttpServletResponse response)
      throws JobExecutionException {
    var workspaceId = workspaceContext.resolve(request, response);
    candidateProfiles.requireCandidateProfile(workspaceId);
    JobParametersBuilder parameters =
        new JobParametersBuilder()
            .addLocalDate("businessDate", businessDate, true)
            .addString("workspaceId", workspaceId.toString(), true);
    if (profileId != null && !profileId.isBlank()) {
      parameters.addString("profileId", profileId.trim(), true);
    }
    JobExecution execution = jobOperator.start(jobDiscoveryJob, parameters.toJobParameters());
    return BatchResponses.from(execution);
  }
}
