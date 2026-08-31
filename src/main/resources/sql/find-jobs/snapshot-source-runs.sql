WITH profiles AS (
    SELECT profile_id, source
    FROM search_profile
    WHERE workspace_id = :workspaceId AND active = TRUE
    UNION
    SELECT profile.profile_id, profile.source
    FROM source_fetch_run source_run
    JOIN search_profile profile ON profile.profile_id = source_run.search_profile_id
    WHERE source_run.job_instance_id = :jobInstanceId
), fetches AS (
    SELECT DISTINCT ON (search_profile_id) *
    FROM source_fetch_run
    WHERE job_instance_id = :jobInstanceId
    ORDER BY search_profile_id, id DESC
)
INSERT INTO workspace_search_source_run (
    workspace_search_run_id, search_profile_id, source, status,
    pages_attempted, pages_fetched, records_received,
    new_records, changed_records, unchanged_records,
    raw_records, normalized_records, sighted_records, scored_records, failure_reason
)
SELECT :workspaceSearchRunId, profile.profile_id, profile.source,
       CASE
           WHEN source_run.id IS NULL THEN 'PENDING'
           WHEN source_run.status = 'COMPLETED' AND source_run.records_received = 0 THEN 'EMPTY'
           ELSE source_run.status
       END,
       COALESCE(source_run.pages_fetched, 0)
           + CASE WHEN source_run.status = 'FAILED' THEN 1 ELSE 0 END,
       COALESCE(source_run.pages_fetched, 0), COALESCE(source_run.records_received, 0),
       COALESCE(source_run.new_records, 0), COALESCE(source_run.changed_records, 0),
       COALESCE(source_run.unchanged_records, 0),
       COALESCE(counts.raw_records, 0), COALESCE(counts.normalized_records, 0),
       COALESCE(counts.sighted_records, 0), COALESCE(counts.scored_records, 0),
       source_run.failure_reason
FROM profiles profile
LEFT JOIN fetches source_run ON source_run.search_profile_id = profile.profile_id
LEFT JOIN LATERAL (
    SELECT count(*)::INTEGER AS raw_records,
           count(normalized.id)::INTEGER AS normalized_records,
           count(sighting.raw_job_posting_id)::INTEGER AS sighted_records,
           count(score.id)::INTEGER AS scored_records
    FROM raw_job_posting raw
    LEFT JOIN normalized_job normalized ON normalized.raw_job_posting_id = raw.id
    LEFT JOIN workspace_job_sighting sighting
        ON sighting.raw_job_posting_id = raw.id AND sighting.workspace_id = :workspaceId
    LEFT JOIN job_score score
        ON score.normalized_job_id = normalized.id
       AND score.candidate_profile_id = :candidateProfileId
    WHERE raw.source_fetch_run_id = source_run.id
) counts ON TRUE
GROUP BY profile.profile_id, profile.source, source_run.id, source_run.status,
         source_run.pages_fetched, source_run.records_received, source_run.new_records,
         source_run.changed_records, source_run.unchanged_records, counts.raw_records,
         counts.normalized_records, counts.sighted_records, counts.scored_records,
         source_run.failure_reason
ON CONFLICT (workspace_search_run_id, search_profile_id)
DO UPDATE SET status = EXCLUDED.status,
              pages_attempted = EXCLUDED.pages_attempted,
              pages_fetched = EXCLUDED.pages_fetched,
              records_received = EXCLUDED.records_received,
              new_records = EXCLUDED.new_records,
              changed_records = EXCLUDED.changed_records,
              unchanged_records = EXCLUDED.unchanged_records,
              raw_records = EXCLUDED.raw_records,
              normalized_records = EXCLUDED.normalized_records,
              sighted_records = EXCLUDED.sighted_records,
              scored_records = EXCLUDED.scored_records,
              failure_reason = EXCLUDED.failure_reason
