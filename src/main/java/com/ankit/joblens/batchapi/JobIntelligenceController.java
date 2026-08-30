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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/batch/intelligence")
public class JobIntelligenceController {

  private final JobOperator jobOperator;
  private final Job jobIntelligenceJob;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public JobIntelligenceController(
      JobOperator jobOperator,
      @Qualifier("jobIntelligenceJob") Job jobIntelligenceJob,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.jobOperator = jobOperator;
    this.jobIntelligenceJob = jobIntelligenceJob;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @PostMapping("/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public JobLaunchResponse run(
      @RequestParam LocalDate businessDate,
      @RequestParam(required = false) Long failAfterItems,
      @RequestParam(required = false, defaultValue = "false") boolean failDuplicateDetection,
      @RequestParam(required = false, defaultValue = "false") boolean failFuzzyDetection,
      HttpServletRequest request,
      HttpServletResponse response)
      throws JobExecutionException {
    if (failAfterItems != null && failAfterItems < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failAfterItems must be positive");
    }
    var workspaceId = workspaceContext.resolve(request, response);
    long candidateProfileId;
    try {
      candidateProfileId = candidateProfiles.requireCandidateProfile(workspaceId);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
    JobParametersBuilder parameters =
        new JobParametersBuilder()
            .addLocalDate("businessDate", businessDate, true)
            .addString("workspaceId", workspaceId.toString(), true)
            .addLong("candidateProfileId", candidateProfileId, true)
            .addString("normalizationVersion", "v1", true)
            .addString("duplicateDetectionVersion", "fuzzy-v1", true);
    if (failAfterItems != null) {
      parameters.addLong("failAfterItems", failAfterItems, false);
    }
    if (failDuplicateDetection) {
      parameters.addLong("failDuplicateDetection", 1L, false);
    }
    if (failFuzzyDetection) {
      parameters.addLong("failFuzzyDetection", 1L, false);
    }
    JobExecution execution = jobOperator.start(jobIntelligenceJob, parameters.toJobParameters());
    return BatchResponses.from(execution);
  }
}
