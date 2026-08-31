SELECT id, candidate_profile_id, business_date, job_instance_id, job_execution_id, status,
       started_at, completed_at, failure_reason
FROM workspace_search_run
WHERE workspace_id = :workspaceId AND job_execution_id = :jobExecutionId
