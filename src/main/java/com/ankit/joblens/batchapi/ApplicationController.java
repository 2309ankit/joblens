package com.ankit.joblens.batchapi;

import com.ankit.joblens.lifecycle.ApplicationLifecycleService;
import com.ankit.joblens.lifecycle.ApplicationQueryRepository;
import com.ankit.joblens.lifecycle.ApplicationStatus;
import com.ankit.joblens.lifecycle.LifecycleNotFoundException;
import com.ankit.joblens.lifecycle.LifecycleValidationException;
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

  public ApplicationController(
      ApplicationLifecycleService service, ApplicationQueryRepository queries) {
    this.service = service;
    this.queries = queries;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody CreateApplicationRequest request) {
    if (request == null) {
      throw new LifecycleValidationException("Application request body is required");
    }
    if (request.normalizedJobId() < 1) {
      throw new LifecycleValidationException("normalizedJobId must be positive");
    }
    long id =
        service.create(
            request.normalizedJobId(), dateOrToday(request.effectiveDate()), request.note());
    return requireApplication(id);
  }

  @PostMapping("/{id}/transitions")
  public Map<String, Object> transition(
      @PathVariable long id, @RequestBody ApplicationTransitionRequest request) {
    if (request == null) {
      throw new LifecycleValidationException("Transition request body is required");
    }
    service.transition(
        id, parseStatus(request.status()), dateOrToday(request.effectiveDate()), request.note());
    return requireApplication(id);
  }

  @GetMapping
  public List<Map<String, Object>> applications(@RequestParam(required = false) String status) {
    return queries.findApplications(status == null ? null : parseStatus(status).name());
  }

  @GetMapping("/{id}")
  public Map<String, Object> application(@PathVariable long id) {
    return requireApplication(id);
  }

  private Map<String, Object> requireApplication(long id) {
    Map<String, Object> application = queries.findApplication(id);
    if (application == null) {
      throw new LifecycleNotFoundException("Application " + id + " was not found");
    }
    return application;
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
