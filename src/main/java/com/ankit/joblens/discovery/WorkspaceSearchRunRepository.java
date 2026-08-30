package com.ankit.joblens.discovery;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceSearchRunRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public WorkspaceSearchRunRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String definitionVersion(UUID workspaceId) {
    List<String> versions =
        jdbc.queryForList(
            load("sql/find-jobs/find-definition-version.sql"),
            Map.of("workspaceId", workspaceId),
            String.class);
    if (versions.isEmpty()) {
      throw new IllegalStateException("Save and confirm job preferences first");
    }
    return versions.getFirst();
  }

  public void record(
      UUID workspaceId, long candidateProfileId, LocalDate businessDate, JobExecution execution) {
    jdbc.update(
        load("sql/find-jobs/record-run.sql"),
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("candidateProfileId", candidateProfileId)
            .addValue("businessDate", businessDate)
            .addValue("jobInstanceId", execution.getJobInstanceId())
            .addValue("jobExecutionId", execution.getId())
            .addValue("status", execution.getStatus().name())
            .addValue("startedAt", execution.getStartTime())
            .addValue("completedAt", execution.getEndTime())
            .addValue(
                "failureReason",
                execution.getFailureExceptions().isEmpty()
                    ? null
                    : execution.getFailureExceptions().getFirst().getMessage()));
  }

  public List<Map<String, Object>> list(UUID workspaceId) {
    return jdbc.queryForList(
        load("sql/find-jobs/list-runs.sql"), Map.of("workspaceId", workspaceId));
  }
}
