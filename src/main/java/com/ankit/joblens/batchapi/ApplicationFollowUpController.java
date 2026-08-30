package com.ankit.joblens.batchapi;

import com.ankit.joblens.lifecycle.ApplicationLifecycleService;
import com.ankit.joblens.lifecycle.ApplicationQueryRepository;
import com.ankit.joblens.lifecycle.LifecycleNotFoundException;
import com.ankit.joblens.lifecycle.LifecycleValidationException;
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

  public ApplicationFollowUpController(
      ApplicationLifecycleService service, ApplicationQueryRepository queries) {
    this.service = service;
    this.queries = queries;
  }

  @GetMapping
  public List<Map<String, Object>> followUps(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate dueOnOrBefore) {
    return queries.findFollowUps(normalizeStatus(status), dueOnOrBefore);
  }

  @PostMapping("/{id}/complete")
  public Map<String, Object> complete(
      @PathVariable long id, @RequestParam(required = false) LocalDate completedOn) {
    service.completeFollowUp(id, completedOn == null ? LocalDate.now() : completedOn);
    Map<String, Object> followUp = queries.findFollowUp(id);
    if (followUp == null) {
      throw new LifecycleNotFoundException("Follow-up " + id + " was not found");
    }
    return followUp;
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
