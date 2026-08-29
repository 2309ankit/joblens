package com.ankit.joblens.intelligence;
import java.sql.ResultSet;
import java.util.List;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;

public class NormalizedJobViewReader implements ItemStreamReader<NormalizedJobView> {
    private final JdbcTemplate jdbc; private final boolean onlyUnextracted; private long currentId;
    public NormalizedJobViewReader(JdbcTemplate jdbc, boolean onlyUnextracted) { this.jdbc=jdbc; this.onlyUnextracted=onlyUnextracted; }
    @Override public NormalizedJobView read() {
        String filter = onlyUnextracted ? "AND (skill_extraction_hash IS DISTINCT FROM normalized_content_hash)" : "";
        List<NormalizedJobView> rows = jdbc.query("SELECT id,source,external_job_id,title,company,location,description_text,employment_type,salary_min,salary_max,salary_currency,remote_type,posted_at,source_url,normalized_content_hash FROM normalized_job WHERE id > ? " + filter + " ORDER BY id LIMIT 1", this::map, currentId);
        if (rows.isEmpty()) return null; currentId = rows.getFirst().id(); return rows.getFirst();
    }
    private NormalizedJobView map(ResultSet rs, int n) throws java.sql.SQLException { return new NormalizedJobView(rs.getLong("id"),rs.getString("source"),rs.getString("external_job_id"),rs.getString("title"),rs.getString("company"),rs.getString("location"),rs.getString("description_text"),rs.getString("employment_type"),rs.getBigDecimal("salary_min"),rs.getBigDecimal("salary_max"),rs.getString("salary_currency"),rs.getString("remote_type"),rs.getObject("posted_at", java.time.OffsetDateTime.class),rs.getString("source_url"),rs.getString("normalized_content_hash")); }
    @Override public void open(ExecutionContext c) throws ItemStreamException { currentId=c.getLong("normalizedJobReader.lastCommittedId",0); }
    @Override public void update(ExecutionContext c) throws ItemStreamException { c.putLong("normalizedJobReader.lastCommittedId",currentId); }
    @Override public void close() throws ItemStreamException {}
}
