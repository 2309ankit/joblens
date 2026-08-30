package com.ankit.joblens.intelligence;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class NormalizedJobViewReader implements ItemStreamReader<NormalizedJobView> {
  private final NamedParameterJdbcTemplate jdbc;
  private final boolean onlyUnextracted;
  private final UUID workspaceId;
  private long currentId;

  public NormalizedJobViewReader(
      NamedParameterJdbcTemplate jdbc, boolean onlyUnextracted, String workspaceId) {
    this.jdbc = jdbc;
    this.onlyUnextracted = onlyUnextracted;
    this.workspaceId = workspaceId == null ? null : UUID.fromString(workspaceId);
  }

  @Override
  public NormalizedJobView read() {
    List<NormalizedJobView> rows =
        jdbc.query(
            load("sql/intelligence/read-normalized-job.sql"),
            new MapSqlParameterSource()
                .addValue("currentId", currentId)
                .addValue("onlyUnextracted", onlyUnextracted)
                .addValue("workspaceId", workspaceId),
            this::map);
    if (rows.isEmpty()) {
      return null;
    }
    currentId = rows.getFirst().id();
    return rows.getFirst();
  }

  private NormalizedJobView map(ResultSet resultSet, int row) throws java.sql.SQLException {
    return new NormalizedJobView(
        resultSet.getLong("id"),
        resultSet.getString("source"),
        resultSet.getString("external_job_id"),
        resultSet.getString("title"),
        resultSet.getString("company"),
        resultSet.getString("location"),
        resultSet.getString("description_text"),
        resultSet.getString("employment_type"),
        resultSet.getBigDecimal("salary_min"),
        resultSet.getBigDecimal("salary_max"),
        resultSet.getString("salary_currency"),
        resultSet.getString("remote_type"),
        resultSet.getObject("posted_at", java.time.OffsetDateTime.class),
        resultSet.getString("source_url"),
        resultSet.getString("normalized_content_hash"));
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    currentId = executionContext.getLong("normalizedJobReader.lastCommittedId", 0);
  }

  @Override
  public void update(ExecutionContext executionContext) throws ItemStreamException {
    executionContext.putLong("normalizedJobReader.lastCommittedId", currentId);
  }

  @Override
  public void close() throws ItemStreamException {
    // NamedParameterJdbcTemplate obtains a connection per query.
  }
}
