package com.ankit.joblens.dashboard;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobViewService {
  private final JobViewRepository repository;

  public JobViewService(JobViewRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public URI recordAndResolve(long jobId, long candidateProfileId, UUID workspaceId) {
    URI uri = validate(repository.findOpenTarget(jobId, workspaceId));
    repository.record(jobId, candidateProfileId);
    return uri;
  }

  public List<Map<String, Object>> list(long candidateProfileId) {
    return repository.list(candidateProfileId);
  }

  private static URI validate(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Job has no source URL");
    }
    URI uri = URI.create(value);
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
      throw new IllegalStateException("Job source URL must use HTTP or HTTPS");
    }
    return uri;
  }
}
