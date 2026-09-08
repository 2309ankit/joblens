package com.ankit.joblens.batchapi;

import com.ankit.joblens.lifecycle.ApplicationLifecyclePolicy;
import com.ankit.joblens.lifecycle.ApplicationLifecycleService;
import com.ankit.joblens.lifecycle.ApplicationQueryRepository;
import com.ankit.joblens.lifecycle.ApplicationStatus;
import com.ankit.joblens.lifecycle.LifecycleNotFoundException;
import com.ankit.joblens.lifecycle.LifecycleValidationException;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "Track this workspace's audited job applications")
public class ApplicationController {

  private final ApplicationLifecycleService service;
  private final ApplicationLifecyclePolicy policy;
  private final ApplicationQueryRepository queries;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public ApplicationController(
      ApplicationLifecycleService service,
      ApplicationLifecyclePolicy policy,
      ApplicationQueryRepository queries,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.service = service;
    this.policy = policy;
    this.queries = queries;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Save a job as an application",
      description =
          "Creates a candidate-owned SAVED application for a job discovered by this workspace and records its first immutable history event.")
  public Map<String, Object> create(
      @RequestBody CreateApplicationRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    WorkspaceCandidate workspace = workspaceCandidate(servletRequest, servletResponse);
    return create(request, workspace.candidateProfileId(), workspace.workspaceId());
  }

  public Map<String, Object> create(CreateApplicationRequest request) {
    return create(request, (Long) null, (UUID) null);
  }

  private Map<String, Object> create(
      CreateApplicationRequest request, Long candidateProfileId, UUID workspaceId) {
    if (request == null) {
      throw new LifecycleValidationException("Application request body is required");
    }
    if (request.normalizedJobId() < 1) {
      throw new LifecycleValidationException("normalizedJobId must be positive");
    }
    long id =
        candidateProfileId == null
            ? service.create(
                request.normalizedJobId(), dateOrToday(request.effectiveDate()), request.note())
            : service.create(
                request.normalizedJobId(),
                candidateProfileId,
                workspaceId,
                dateOrToday(request.effectiveDate()),
                request.note());
    return requireApplication(id, candidateProfileId);
  }

  @PostMapping("/{id}/transitions")
  @Operation(
      summary = "Move an application forward",
      description =
          "Applies an allowed forward-only lifecycle transition and appends immutable status history. Invalid, backward, and cross-workspace requests are rejected.")
  public Map<String, Object> transition(
      @PathVariable long id,
      @RequestBody ApplicationTransitionRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return transition(id, request, candidateProfileId(servletRequest, servletResponse));
  }

  public Map<String, Object> transition(long id, ApplicationTransitionRequest request) {
    return transition(id, request, null);
  }

  private Map<String, Object> transition(
      long id, ApplicationTransitionRequest request, Long candidateProfileId) {
    if (request == null) {
      throw new LifecycleValidationException("Transition request body is required");
    }
    service.transition(
        id,
        candidateProfileId,
        parseStatus(request.status()),
        dateOrToday(request.effectiveDate()),
        request.note());
    return requireApplication(id, candidateProfileId);
  }

  @GetMapping
  @Operation(summary = "List this workspace's applications")
  public List<Map<String, Object>> applications(
      @RequestParam(required = false) String status,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return applications(
        status == null ? null : parseStatus(status).name(),
        candidateProfileId(servletRequest, servletResponse));
  }

  public List<Map<String, Object>> applications(String status) {
    return applications(status == null ? null : parseStatus(status).name(), null);
  }

  private List<Map<String, Object>> applications(String status, Long candidateProfileId) {
    return queries.findApplications(status, candidateProfileId).stream()
        .map(this::withAllowedTransitions)
        .toList();
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Inspect one application",
      description =
          "Returns current state, immutable transition history, and generated follow-ups.")
  public Map<String, Object> application(
      @PathVariable long id,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return requireApplication(id, candidateProfileId(servletRequest, servletResponse));
  }

  public Map<String, Object> application(long id) {
    return requireApplication(id, null);
  }

  private Map<String, Object> requireApplication(long id, Long candidateProfileId) {
    Map<String, Object> application = queries.findApplication(id, candidateProfileId);
    if (application == null) {
      throw new LifecycleNotFoundException("Application " + id + " was not found");
    }
    return withAllowedTransitions(application);
  }

  private Map<String, Object> withAllowedTransitions(Map<String, Object> application) {
    Map<String, Object> result = new LinkedHashMap<>(application);
    ApplicationStatus current = ApplicationStatus.valueOf((String) application.get("status"));
    result.put(
        "allowedTransitions",
        policy.allowedTransitions(current).stream().map(ApplicationStatus::name).sorted().toList());
    return result;
  }

  private long candidateProfileId(
      HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    return candidateProfiles.requireCandidateProfile(
        workspaceContext.resolve(servletRequest, servletResponse));
  }

  private WorkspaceCandidate workspaceCandidate(
      HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    UUID workspaceId = workspaceContext.resolve(servletRequest, servletResponse);
    return new WorkspaceCandidate(
        workspaceId, candidateProfiles.requireCandidateProfile(workspaceId));
  }

  private record WorkspaceCandidate(UUID workspaceId, long candidateProfileId) {}

  private static ApplicationStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      throw new LifecycleValidationException("Application status is required");
    }
    try {
      return ApplicationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new LifecycleValidationException("Unsupported application status: " + value);
    }
  }

  private static LocalDate dateOrToday(LocalDate date) {
    return date == null ? LocalDate.now() : date;
  }
}
