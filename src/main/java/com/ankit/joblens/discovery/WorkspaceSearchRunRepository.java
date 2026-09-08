package com.ankit.joblens.discovery;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkspaceSearchRunRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final FailureReasonSanitizer failureReasons;

  public WorkspaceSearchRunRepository(
      NamedParameterJdbcTemplate jdbc, FailureReasonSanitizer failureReasons) {
    this.jdbc = jdbc;
    this.failureReasons = failureReasons;
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

  @Transactional
  public long record(
      UUID workspaceId, long candidateProfileId, LocalDate businessDate, JobExecution execution) {
    Long runId =
        jdbc.queryForObject(
            load("sql/find-jobs/record-run.sql"),
            new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("candidateProfileId", candidateProfileId)
                .addValue("businessDate", businessDate)
                .addValue("jobInstanceId", execution.getJobInstanceId())
                .addValue("jobExecutionId", execution.getId())
                .addValue("status", execution.getStatus().name())
                .addValue(
                    "startedAt",
                    execution.getStartTime() == null
                        ? execution.getCreateTime()
                        : execution.getStartTime())
                .addValue("completedAt", execution.getEndTime())
                .addValue(
                    "failureReason",
                    execution.getFailureExceptions().isEmpty()
                        ? null
                        : failureReasons.sanitize(execution.getFailureExceptions().getFirst())),
            Long.class);
    jdbc.update(
        load("sql/find-jobs/snapshot-source-runs.sql"),
        new MapSqlParameterSource()
            .addValue("workspaceSearchRunId", runId)
            .addValue("workspaceId", workspaceId)
            .addValue("candidateProfileId", candidateProfileId)
            .addValue("jobInstanceId", execution.getJobInstanceId()));
    return runId;
  }

  /** Marks only an owned, still-running product projection stale; Batch recovery stays explicit. */
  public void markStale(UUID workspaceId, long jobExecutionId) {
    jdbc.update(
        load("sql/find-jobs/mark-stale-run.sql"),
        Map.of("workspaceId", workspaceId, "jobExecutionId", jobExecutionId));
  }

  public List<Map<String, Object>> list(UUID workspaceId) {
    return jdbc.queryForList(
        load("sql/find-jobs/list-runs.sql"), Map.of("workspaceId", workspaceId));
  }

  public Optional<FindJobsRunDetail> find(UUID workspaceId, long jobExecutionId) {
    List<FindJobsRunSummary> run =
        jdbc.query(
            load("sql/find-jobs/find-run.sql"),
            Map.of("workspaceId", workspaceId, "jobExecutionId", jobExecutionId),
            (resultSet, rowNumber) ->
                new FindJobsRunSummary(
                    resultSet.getLong("id"),
                    resultSet.getLong("candidate_profile_id"),
                    resultSet.getObject("business_date", LocalDate.class),
                    resultSet.getLong("job_instance_id"),
                    resultSet.getLong("job_execution_id"),
                    productStatus(resultSet.getString("status")),
                    "PENDING",
                    instant(resultSet.getTimestamp("started_at")),
                    instant(resultSet.getTimestamp("completed_at")),
                    resultSet.getString("failure_reason")));
    if (run.isEmpty()) {
      return Optional.empty();
    }
    List<SourceRunSummary> sources =
        jdbc.query(
            load("sql/find-jobs/list-run-sources.sql"),
            Map.of("workspaceId", workspaceId, "jobExecutionId", jobExecutionId),
            (resultSet, rowNumber) ->
                new SourceRunSummary(
                    resultSet.getString("search_profile_id"),
                    resultSet.getString("source"),
                    resultSet.getString("country_code"),
                    resultSet.getString("location"),
                    resultSet.getString("query_text"),
                    resultSet.getString("status"),
                    resultSet.getInt("pages_attempted"),
                    resultSet.getInt("pages_fetched"),
                    resultSet.getInt("records_received"),
                    resultSet.getInt("new_records"),
                    resultSet.getInt("changed_records"),
                    resultSet.getInt("unchanged_records"),
                    resultSet.getInt("raw_records"),
                    resultSet.getInt("normalized_records"),
                    resultSet.getInt("sighted_records"),
                    resultSet.getInt("scored_records"),
                    resultSet.getString("first_zero_stage"),
                    resultSet.getString("failure_reason")));
    FindJobsRunSummary original = run.getFirst();
    FindJobsRunSummary summary =
        new FindJobsRunSummary(
            original.id(),
            original.candidateProfileId(),
            original.businessDate(),
            original.jobInstanceId(),
            original.jobExecutionId(),
            original.status(),
            outcome(original.status(), sources),
            original.startedAt(),
            original.completedAt(),
            original.failureReason());
    return Optional.of(new FindJobsRunDetail(summary, List.copyOf(sources)));
  }

  public Optional<FindJobsRunDetail> latest(UUID workspaceId) {
    List<Long> executions =
        jdbc.queryForList(
            load("sql/find-jobs/find-latest-run-execution.sql"),
            Map.of("workspaceId", workspaceId),
            Long.class);
    return executions.isEmpty() ? Optional.empty() : find(workspaceId, executions.getFirst());
  }

  /** Resolves the private Batch execution only after proving ownership of the product run. */
  public Optional<Long> executionId(UUID workspaceId, long runId) {
    List<Long> executionIds =
        jdbc.queryForList(
            load("sql/find-jobs/find-execution-for-run.sql"),
            Map.of("workspaceId", workspaceId, "runId", runId),
            Long.class);
    return executionIds.stream().findFirst();
  }

  private static String outcome(String status, List<SourceRunSummary> sources) {
    if (status.equals("STALE")) {
      return "STALE";
    }
    boolean failed = sources.stream().anyMatch(source -> source.status().equals("FAILED"));
    boolean successful =
        sources.stream()
            .anyMatch(
                source -> source.status().equals("COMPLETED") || source.status().equals("EMPTY"));
    if (failed) {
      return successful ? "PARTIAL" : "FAILED";
    }
    if (status.equals("FAILED")) {
      return "FAILED";
    }
    if (status.equals("ACTIVE")) {
      return "RUNNING";
    }
    if (sources.stream().anyMatch(source -> source.status().equals("RUNNING"))) {
      return "RUNNING";
    }
    if (!sources.isEmpty()
        && sources.stream()
            .allMatch(
                source -> source.status().equals("COMPLETED") || source.status().equals("EMPTY"))) {
      return "COMPLETED";
    }
    return "PENDING";
  }

  private static String productStatus(String batchStatus) {
    return switch (batchStatus) {
      case "STARTING", "STARTED", "STOPPING" -> "ACTIVE";
      case "COMPLETED" -> "COMPLETED";
      case "STALE" -> "STALE";
      default -> "FAILED";
    };
  }

  private static java.time.Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
