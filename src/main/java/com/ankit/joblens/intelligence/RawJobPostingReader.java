package com.ankit.joblens.intelligence;

import java.util.List;

import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;

public class RawJobPostingReader implements ItemStreamReader<RawJobPosting> {

    static final String LAST_COMMITTED_ID = "rawJobPostingReader.lastCommittedId";

    private final JdbcTemplate jdbcTemplate;
    private long currentId;

    public RawJobPostingReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RawJobPosting read() {
        List<RawJobPosting> rows = jdbcTemplate.query("""
                SELECT id, source, external_job_id, source_url, payload_hash,
                       raw_payload_json::text AS raw_json
                FROM raw_job_posting
                WHERE processing_status IN ('NEW', 'FAILED') AND id > ?
                ORDER BY id
                LIMIT 1
                """, (resultSet, rowNumber) -> new RawJobPosting(
                        resultSet.getLong("id"),
                        resultSet.getString("source"),
                        resultSet.getString("external_job_id"),
                        resultSet.getString("source_url"),
                        resultSet.getString("payload_hash"),
                        resultSet.getString("raw_json")), currentId);
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
        // JdbcTemplate obtains a connection per query; there is no reader resource to close.
    }
}
