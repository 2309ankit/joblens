package com.ankit.joblens.batchapi;

import com.ankit.joblens.discovery.FindJobsService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch/find-jobs")
@Tag(name = "Find jobs", description = "Run and inspect the complete workspace job-search pipeline")
public class FindJobsController {
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;
  private final FindJobsService service;

  public FindJobsController(
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles,
      FindJobsService service) {
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
    this.service = service;
  }

  @PostMapping("/run")
  @Operation(
      summary = "Find and rank jobs now",
      description =
          "Uses this browser workspace's confirmed resume profile and preferences. The restartable Spring Batch job discovers raw postings, normalizes them, extracts skills, detects duplicates, and calculates this candidate's scores.")
  public JobLaunchResponse run(
      @RequestParam(required = false) LocalDate businessDate,
      HttpServletRequest request,
      HttpServletResponse response)
      throws JobExecutionException {
    UUID workspaceId = workspaceContext.resolve(request, response);
    long candidateProfileId = candidateProfiles.requireCandidateProfile(workspaceId);
    return BatchResponses.from(
        service.run(
            workspaceId,
            candidateProfileId,
            businessDate == null ? LocalDate.now() : businessDate));
  }

  @GetMapping("/runs")
  @Operation(summary = "List this workspace's Find jobs runs")
  public List<Map<String, Object>> runs(HttpServletRequest request, HttpServletResponse response) {
    return service.runs(workspaceContext.resolve(request, response));
  }
}
