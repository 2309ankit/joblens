package com.ankit.joblens.batchapi;

import com.ankit.joblens.lifecycle.ApplicationLifecycleService;
import com.ankit.joblens.lifecycle.ApplicationQueryRepository;
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
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follow-ups")
public class ApplicationFollowUpController {

  private static final Set<String> STATUSES = Set.of("OPEN", "COMPLETED", "CANCELLED");

  private final ApplicationLifecycleService service;
  private final ApplicationQueryRepository queries;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public ApplicationFollowUpController(
      ApplicationLifecycleService service,
      ApplicationQueryRepository queries,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.service = service;
    this.queries = queries;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @GetMapping
  public List<Map<String, Object>> followUps(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate dueOnOrBefore,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return queries.findFollowUps(
        normalizeStatus(status),
        dueOnOrBefore,
        candidateProfileId(servletRequest, servletResponse));
  }

  public List<Map<String, Object>> followUps(String status, LocalDate dueOnOrBefore) {
    return queries.findFollowUps(normalizeStatus(status), dueOnOrBefore);
  }

  @PostMapping("/{id}/complete")
  public Map<String, Object> complete(
      @PathVariable long id,
      @RequestParam(required = false) LocalDate completedOn,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return complete(id, completedOn, candidateProfileId(servletRequest, servletResponse));
  }

  public Map<String, Object> complete(long id, LocalDate completedOn) {
    return complete(id, completedOn, null);
  }

  private Map<String, Object> complete(long id, LocalDate completedOn, Long candidateProfileId) {
    service.completeFollowUp(
        id, candidateProfileId, completedOn == null ? LocalDate.now() : completedOn);
    Map<String, Object> followUp = queries.findFollowUp(id, candidateProfileId);
    if (followUp == null) {
      throw new LifecycleNotFoundException("Follow-up " + id + " was not found");
    }
    return followUp;
  }

  private long candidateProfileId(
      HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    return candidateProfiles.requireCandidateProfile(
        workspaceContext.resolve(servletRequest, servletResponse));
  }

  private static String normalizeStatus(String status) {
    if (status == null) {
      return null;
    }
    String normalized = status.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(normalized)) {
      throw new LifecycleValidationException("Unsupported follow-up status: " + status);
    }
    return normalized;
  }
}
