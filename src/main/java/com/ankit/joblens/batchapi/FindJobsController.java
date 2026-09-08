package com.ankit.joblens.batchapi;

import com.ankit.joblens.discovery.FindJobsLaunchResponse;
import com.ankit.joblens.discovery.FindJobsRunDetail;
import com.ankit.joblens.discovery.FindJobsService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
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
          "Uses this browser workspace's confirmed resume profile and preferences. The durable workflow discovers raw postings, normalizes them, extracts skills, detects duplicates, and calculates this candidate's scores.")
  public FindJobsLaunchResponse run(
      @RequestParam(required = false) LocalDate businessDate,
      HttpServletRequest request,
      HttpServletResponse response)
      throws JobExecutionException {
    UUID workspaceId = workspaceContext.resolve(request, response);
    long candidateProfileId = candidateProfiles.requireCandidateProfile(workspaceId);
    var execution =
        service.run(
            workspaceId, candidateProfileId, businessDate == null ? LocalDate.now() : businessDate);
    return response(workspaceId, execution.getId());
  }

  @GetMapping("/runs")
  @Operation(summary = "List this workspace's Find jobs runs")
  public java.util.List<Map<String, Object>> runs(
      HttpServletRequest request, HttpServletResponse response) {
    return service.runs(workspaceContext.resolve(request, response));
  }

  @GetMapping("/runs/{runId}")
  @Operation(
      summary = "Inspect one Find jobs run",
      description =
          "Returns a workspace-safe source-and-market breakdown: query, country, location, product status, pages, received/new/changed/unchanged records, normalized and scored counts, first zero stage, and a sanitized failure reason. PARTIAL means at least one source completed before another source failed; the run remains restartable.")
  public FindJobsRunDetail runDetail(
      @PathVariable long runId, HttpServletRequest request, HttpServletResponse response) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    return service
        .detailByRunId(workspaceId, runId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
  }

  @PostMapping("/runs/{runId}/restart")
  @Operation(
      summary = "Restart a failed Find jobs run",
      description =
          "Restarts only a failed or stale run owned by this browser workspace. Completed source checkpoints are kept while unfinished work resumes before normalization and scoring continue.")
  public FindJobsLaunchResponse restart(
      @PathVariable long runId, HttpServletRequest request, HttpServletResponse response)
      throws JobExecutionException {
    UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      var execution = service.restartByRunId(workspaceId, runId);
      return response(workspaceId, execution.getId());
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found", exception);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This run cannot be restarted in its current state.", exception);
    }
  }

  private FindJobsLaunchResponse response(UUID workspaceId, long executionId) {
    FindJobsRunDetail detail =
        service
            .detail(workspaceId, executionId)
            .orElseThrow(() -> new IllegalStateException("Find Jobs run was not recorded"));
    return new FindJobsLaunchResponse(
        detail.run().id(), detail.run().status(), detail.run().outcome());
  }
}
