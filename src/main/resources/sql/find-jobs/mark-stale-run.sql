UPDATE workspace_search_run
SET status = 'STALE',
    failure_reason = 'The search stopped updating before completion. Restart it to resume from the last checkpoint.'
WHERE workspace_id = :workspaceId
  AND job_execution_id = :jobExecutionId
  AND status IN ('STARTING', 'STARTED', 'STOPPING')
