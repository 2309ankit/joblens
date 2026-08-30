package com.ankit.joblens.batchapi;

import com.ankit.joblens.lifecycle.ApplicationLifecycleService;
import com.ankit.joblens.lifecycle.ApplicationQueryRepository;
import com.ankit.joblens.lifecycle.ApplicationStatus;
import com.ankit.joblens.lifecycle.LifecycleNotFoundException;
import com.ankit.joblens.lifecycle.LifecycleValidationException;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class ApplicationController {

  private final ApplicationLifecycleService service;
  private final ApplicationQueryRepository queries;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public ApplicationController(
      ApplicationLifecycleService service,
      ApplicationQueryRepository queries,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.service = service;
    this.queries = queries;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @RequestBody CreateApplicationRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return create(request, candidateProfileId(servletRequest, servletResponse));
  }

  public Map<String, Object> create(CreateApplicationRequest request) {
    return create(request, null);
  }

  private Map<String, Object> create(CreateApplicationRequest request, Long candidateProfileId) {
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
                dateOrToday(request.effectiveDate()),
                request.note());
    return requireApplication(id, candidateProfileId);
  }

  @PostMapping("/{id}/transitions")
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
  public List<Map<String, Object>> applications(
      @RequestParam(required = false) String status,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return queries.findApplications(
        status == null ? null : parseStatus(status).name(),
        candidateProfileId(servletRequest, servletResponse));
  }

  public List<Map<String, Object>> applications(String status) {
    return queries.findApplications(status == null ? null : parseStatus(status).name());
  }

  @GetMapping("/{id}")
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
    return application;
  }

  private long candidateProfileId(
      HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    return candidateProfiles.requireCandidateProfile(
        workspaceContext.resolve(servletRequest, servletResponse));
  }

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
