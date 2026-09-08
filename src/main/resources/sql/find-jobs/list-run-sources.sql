SELECT source.search_profile_id, source.source,
       CASE WHEN source.source IN ('ADZUNA', 'JOOBLE')
            THEN upper(profile.source_key)
       END AS country_code,
       profile.location, source.query_text, source.status, source.pages_attempted,
       source.pages_fetched,
       source.records_received, source.new_records, source.changed_records,
       source.unchanged_records, source.raw_records, source.normalized_records,
       source.sighted_records, source.scored_records, source.first_zero_stage, source.failure_reason
FROM workspace_search_source_run source
JOIN workspace_search_run run ON run.id = source.workspace_search_run_id
JOIN search_profile profile ON profile.profile_id = source.search_profile_id
WHERE run.workspace_id = :workspaceId AND run.job_execution_id = :jobExecutionId
ORDER BY source.id
