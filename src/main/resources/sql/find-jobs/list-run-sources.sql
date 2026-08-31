SELECT source.search_profile_id, source.source, source.status, source.pages_attempted,
       source.pages_fetched,
       source.records_received, source.new_records, source.changed_records,
       source.unchanged_records, source.raw_records, source.normalized_records,
       source.sighted_records, source.scored_records, source.failure_reason
FROM workspace_search_source_run source
JOIN workspace_search_run run ON run.id = source.workspace_search_run_id
WHERE run.workspace_id = :workspaceId AND run.job_execution_id = :jobExecutionId
ORDER BY source.id
