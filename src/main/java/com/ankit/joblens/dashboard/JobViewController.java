package com.ankit.joblens.dashboard;

import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/job-views")
@Tag(name = "Job views", description = "Open source listings and inspect viewed jobs")
public class JobViewController {
  private final JobViewService service;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public JobViewController(
      JobViewService service,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.service = service;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @GetMapping
  @Operation(summary = "List viewed jobs in this workspace")
  public List<Map<String, Object>> list(HttpServletRequest request, HttpServletResponse response) {
    return service.list(candidateProfileId(request, response));
  }

  @GetMapping("/{jobId}/open")
  @Operation(summary = "Mark a job viewed and open its original listing")
  public ResponseEntity<Void> open(
      @PathVariable long jobId, HttpServletRequest request, HttpServletResponse response) {
    try {
      UUID workspaceId = workspaceContext.resolve(request, response);
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(
              service.recordAndResolve(
                  jobId, candidateProfiles.requireCandidateProfile(workspaceId), workspaceId))
          .build();
    } catch (JobViewNotFoundException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), exception);
    }
  }

  private long candidateProfileId(HttpServletRequest request, HttpServletResponse response) {
    try {
      return candidateProfiles.requireCandidateProfile(workspaceContext.resolve(request, response));
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }
}
