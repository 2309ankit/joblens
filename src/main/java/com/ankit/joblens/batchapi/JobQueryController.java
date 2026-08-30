package com.ankit.joblens.batchapi;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Inspect normalized jobs and their scores")
public class JobQueryController {
  private final NamedParameterJdbcTemplate jdbc;
  private final DuplicateQueryRepository duplicateQueryRepository;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public JobQueryController(
      NamedParameterJdbcTemplate jdbc,
      DuplicateQueryRepository duplicateQueryRepository,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.jdbc = jdbc;
    this.duplicateQueryRepository = duplicateQueryRepository;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @GetMapping
  @Operation(
      summary = "List ranked jobs",
      description = "Returns normalized jobs ordered by candidate-fit score")
  public List<Map<String, Object>> jobs(HttpServletRequest request, HttpServletResponse response) {
    return jobs(candidateProfileId(request, response));
  }

  public List<Map<String, Object>> jobs() {
    return jobs(null);
  }

  private List<Map<String, Object>> jobs(Long candidateProfileId) {
    return jdbc.query(
        load("sql/job-query/list-jobs.sql"),
        Map.of("candidateProfileId", candidateProfileId == null ? 0L : candidateProfileId),
        (rs, n) -> row(rs));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get job details",
      description = "Returns job fields, skills, score reasons, duplicates, and similarity matches")
  public Map<String, Object> job(
      @PathVariable long id, HttpServletRequest request, HttpServletResponse response) {
    return job(id, candidateProfileId(request, response));
  }

  public Map<String, Object> job(long id) {
    return job(id, null);
  }

  private Map<String, Object> job(long id, Long candidateProfileId) {
    long scoreProfileId = candidateProfileId == null ? 0L : candidateProfileId;
    var rows =
        jdbc.query(
            load("sql/job-query/find-job.sql"),
            Map.of("id", id, "candidateProfileId", scoreProfileId),
            (rs, n) -> row(rs));
    if (rows.isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
    }
    var result = new LinkedHashMap<>(rows.getFirst());
    result.put(
        "skills",
        jdbc.queryForList(
            load("sql/job-query/list-job-skills.sql"), Map.of("id", id), String.class));
    result.put(
        "scoreReasons",
        jdbc.queryForList(
            load("sql/job-query/list-score-reasons.sql"),
            Map.of("id", id, "candidateProfileId", scoreProfileId)));
    result.put("duplicateCluster", duplicateQueryRepository.findClusterForJob(id));
    result.put("similarityMatches", duplicateQueryRepository.findSimilaritiesForJob(id));
    return result;
  }

  private long candidateProfileId(HttpServletRequest request, HttpServletResponse response) {
    return candidateProfiles.requireCandidateProfile(workspaceContext.resolve(request, response));
  }

  private static Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
    var m = new LinkedHashMap<String, Object>();
    var md = rs.getMetaData();
    for (int i = 1; i <= md.getColumnCount(); i++) {
      m.put(md.getColumnLabel(i), rs.getObject(i));
    }
    return m;
  }
}
