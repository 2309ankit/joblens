package com.ankit.joblens.discovery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import com.ankit.joblens.searchprofile.SearchProfile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryPersistenceService {

    private static final String RAW_UPSERT = """
            INSERT INTO raw_job_posting (
                source, external_job_id, search_profile_id, source_fetch_run_id,
                source_url, payload_hash, raw_payload_json, job_execution_id
            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            ON CONFLICT (source, external_job_id) DO UPDATE SET
                search_profile_id = EXCLUDED.search_profile_id,
                source_fetch_run_id = EXCLUDED.source_fetch_run_id,
                source_url = EXCLUDED.source_url,
                last_seen_at = CURRENT_TIMESTAMP,
                raw_payload_json = CASE
                    WHEN raw_job_posting.payload_hash <> EXCLUDED.payload_hash
                    THEN EXCLUDED.raw_payload_json
                    ELSE raw_job_posting.raw_payload_json
                END,
                payload_hash = EXCLUDED.payload_hash,
                job_execution_id = EXCLUDED.job_execution_id,
                updated_at = CASE
                    WHEN raw_job_posting.payload_hash <> EXCLUDED.payload_hash
                    THEN CURRENT_TIMESTAMP
                    ELSE raw_job_posting.updated_at
                END
            """;

    private final JdbcTemplate jdbcTemplate;

    public DiscoveryPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SearchProfile findNextActiveProfile(String lastCompletedProfileId, String requestedProfileId) {
        String baseSql = """
                SELECT profile_id, source, source_key, keywords, location, include_skills,
                       exclude_skills, employment_type, active
                FROM search_profile
                WHERE active = true
                  AND source = 'ADZUNA'
                """;
        String sql;
        Object[] parameters;
        if (requestedProfileId != null) {
            sql = baseSql + " AND profile_id = ? ORDER BY profile_id LIMIT 1";
            parameters = new Object[] {requestedProfileId};
        } else if (lastCompletedProfileId != null) {
            sql = baseSql + " AND profile_id > ? ORDER BY profile_id LIMIT 1";
            parameters = new Object[] {lastCompletedProfileId};
        } else {
            sql = baseSql + " ORDER BY profile_id LIMIT 1";
            parameters = new Object[0];
        }

        List<SearchProfile> profiles = jdbcTemplate.query(sql, (resultSet, rowNumber) -> new SearchProfile(
                resultSet.getString("profile_id"),
                resultSet.getString("source"),
                resultSet.getString("source_key"),
                resultSet.getString("keywords"),
                resultSet.getString("location"),
                resultSet.getString("include_skills"),
                resultSet.getString("exclude_skills"),
                resultSet.getString("employment_type"),
                resultSet.getBoolean("active")), parameters);
        return profiles.isEmpty() ? null : profiles.getFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FetchRunState startOrResumeFetchRun(
            SearchProfile profile, long jobInstanceId, long jobExecutionId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO source_fetch_run (
                    source, search_profile_id, status, job_instance_id, job_execution_id
                ) VALUES (?, ?, 'RUNNING', ?, ?)
                ON CONFLICT (job_instance_id, search_profile_id) DO UPDATE SET
                    status = 'RUNNING',
                    completed_at = NULL,
                    failure_reason = NULL,
                    job_execution_id = EXCLUDED.job_execution_id
                RETURNING id, next_page, status
                """, (resultSet, rowNumber) -> new FetchRunState(
                        resultSet.getLong("id"),
                        resultSet.getInt("next_page"),
                        resultSet.getString("status")),
                profile.source(), profile.profileId(), jobInstanceId, jobExecutionId);
    }

    public FetchRunState fetchRun(long fetchRunId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, next_page, status FROM source_fetch_run WHERE id = ?",
                (resultSet, rowNumber) -> new FetchRunState(
                        resultSet.getLong("id"),
                        resultSet.getInt("next_page"),
                        resultSet.getString("status")),
                fetchRunId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistPage(long fetchRunId, SearchProfile profile, JobPage page, long jobExecutionId) {
        Integer expectedPage = jdbcTemplate.queryForObject(
                "SELECT next_page FROM source_fetch_run WHERE id = ? FOR UPDATE", Integer.class, fetchRunId);
        if (expectedPage == null) {
            throw new IllegalStateException("Fetch run does not exist: " + fetchRunId);
        }
        if (page.page() < expectedPage) {
            return;
        }
        if (page.page() > expectedPage) {
            throw new IllegalStateException(
                    "Fetch run " + fetchRunId + " expected page " + expectedPage + " but received " + page.page());
        }

        List<RawSourceJob> jobs = page.jobs();
        if (!jobs.isEmpty()) {
            jdbcTemplate.batchUpdate(RAW_UPSERT, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    RawSourceJob job = jobs.get(index);
                    statement.setString(1, JobSource.ADZUNA.name());
                    statement.setString(2, job.externalJobId());
                    statement.setString(3, profile.profileId());
                    statement.setLong(4, fetchRunId);
                    statement.setString(5, job.sourceUrl());
                    statement.setString(6, job.payloadHash());
                    statement.setString(7, job.rawJson());
                    statement.setLong(8, jobExecutionId);
                }

                @Override
                public int getBatchSize() {
                    return jobs.size();
                }
            });
        }

        jdbcTemplate.update("""
                UPDATE source_fetch_run
                SET pages_fetched = pages_fetched + 1,
                    records_received = records_received + ?,
                    next_page = ?,
                    status = 'RUNNING',
                    failure_reason = NULL,
                    job_execution_id = ?
                WHERE id = ?
                """, jobs.size(), page.page() + 1, jobExecutionId, fetchRunId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFetchRun(long fetchRunId, long jobExecutionId) {
        jdbcTemplate.update("""
                UPDATE source_fetch_run
                SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP,
                    failure_reason = NULL, job_execution_id = ?
                WHERE id = ?
                """, jobExecutionId, fetchRunId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failFetchRun(long fetchRunId, long jobExecutionId, Throwable failure) {
        String reason = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "No failure message" : failure.getMessage());
        if (reason.length() > 2000) {
            reason = reason.substring(0, 2000);
        }
        jdbcTemplate.update("""
                UPDATE source_fetch_run
                SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP,
                    failure_reason = ?, job_execution_id = ?
                WHERE id = ?
                """, reason, jobExecutionId, fetchRunId);
    }
}
