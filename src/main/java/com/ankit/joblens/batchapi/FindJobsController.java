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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

  @GetMapping("/runs/{jobExecutionId}")
  @Operation(
      summary = "Inspect one Find jobs run",
      description =
          "Returns a workspace-safe, immutable source breakdown: source status, pages, received/new/changed/unchanged records, normalized and scored counts, and a sanitized failure reason. PARTIAL means at least one source completed before another source failed; the Batch execution remains FAILED and restartable.")
  public com.ankit.joblens.discovery.FindJobsRunDetail runDetail(
      @PathVariable long jobExecutionId, HttpServletRequest request, HttpServletResponse response) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    return service
        .detail(workspaceId, jobExecutionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
  }

  @PostMapping("/runs/{jobExecutionId}/restart")
  @Operation(
      summary = "Restart a failed Find jobs run",
      description =
          "Restarts only a failed execution owned by this browser workspace. Spring Batch keeps completed source checkpoints and retries the unfinished source before continuing normalization and scoring.")
  public JobLaunchResponse restart(
      @PathVariable long jobExecutionId, HttpServletRequest request, HttpServletResponse response)
      throws JobExecutionException {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      return BatchResponses.from(service.restart(workspaceId, jobExecutionId));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }
}
