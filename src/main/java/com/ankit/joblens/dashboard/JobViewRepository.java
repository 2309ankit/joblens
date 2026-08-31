package com.ankit.joblens.dashboard;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JobViewRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JobViewRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public OpenTarget findOpenTarget(long jobId, UUID workspaceId) {
    return jdbc
        .query(
            load("sql/job-view/find-open-target.sql"),
            Map.of("jobId", jobId, "workspaceId", workspaceId),
            (rs, row) ->
                new OpenTarget(
                    rs.getString("source_url"), rs.getString("source"), rs.getString("source_key")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new JobViewNotFoundException(jobId));
  }

  public void record(long jobId, long candidateProfileId) {
    jdbc.update(
        load("sql/job-view/record-view.sql"),
        Map.of("jobId", jobId, "candidateProfileId", candidateProfileId));
  }

  public List<Map<String, Object>> list(long candidateProfileId) {
    return jdbc.queryForList(
        load("sql/job-view/list-views.sql"), Map.of("candidateProfileId", candidateProfileId));
  }

  public record OpenTarget(String sourceUrl, String source, String sourceKey) {}
}
