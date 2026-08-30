package com.ankit.joblens.intelligence;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.List;
import java.util.UUID;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class RawJobPostingReader implements ItemStreamReader<RawJobPosting> {
  static final String LAST_COMMITTED_ID = "rawJobPostingReader.lastCommittedId";

  private final NamedParameterJdbcTemplate jdbc;
  private final UUID workspaceId;
  private long currentId;

  public RawJobPostingReader(NamedParameterJdbcTemplate jdbc, String workspaceId) {
    this.jdbc = jdbc;
    this.workspaceId = workspaceId == null ? null : UUID.fromString(workspaceId);
  }

  @Override
  public RawJobPosting read() {
    List<RawJobPosting> rows =
        jdbc.query(
            load("sql/intelligence/read-raw-job.sql"),
            new MapSqlParameterSource()
                .addValue("currentId", currentId)
                .addValue("workspaceId", workspaceId),
            (resultSet, rowNumber) ->
                new RawJobPosting(
                    resultSet.getLong("id"),
                    resultSet.getString("source"),
                    resultSet.getString("external_job_id"),
                    resultSet.getString("source_url"),
                    resultSet.getString("payload_hash"),
                    resultSet.getString("raw_json")));
    if (rows.isEmpty()) {
      return null;
    }
    RawJobPosting row = rows.getFirst();
    currentId = row.id();
    return row;
  }

  @Override
  public void open(ExecutionContext executionContext) throws ItemStreamException {
    currentId = executionContext.getLong(LAST_COMMITTED_ID, 0L);
  }

  @Override
  public void update(ExecutionContext executionContext) throws ItemStreamException {
    executionContext.putLong(LAST_COMMITTED_ID, currentId);
  }

  @Override
  public void close() throws ItemStreamException {
    // NamedParameterJdbcTemplate obtains a connection per query.
  }
}
