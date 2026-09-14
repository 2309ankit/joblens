SELECT source.search_profile_id, source.source,
       CASE WHEN source.source IN ('ADZUNA', 'JOOBLE')
            THEN upper(profile.source_key)
       END AS country_code,
       profile.location, source.query_text, source.status, source.pages_attempted,
       source.pages_fetched,
       source.records_received, source.new_records, source.changed_records,
       source.unchanged_records, source.raw_records, source.normalized_records,
       source.sighted_records, source.scored_records, source.first_zero_stage, source.failure_reason,
       CASE WHEN plan.id IS NULL THEN 'DETERMINISTIC' ELSE 'LLM_NEBIUS' END AS query_planning_source,
       plan.rationale AS query_planning_rationale
FROM workspace_search_source_run source
JOIN workspace_search_run run ON run.id = source.workspace_search_run_id
JOIN search_profile profile ON profile.profile_id = source.search_profile_id
LEFT JOIN query_plan_decision plan
  ON plan.job_execution_id = run.job_execution_id
  AND plan.search_profile_id = source.search_profile_id
  AND plan.applied = TRUE
WHERE run.workspace_id = :workspaceId AND run.job_execution_id = :jobExecutionId
ORDER BY source.id
