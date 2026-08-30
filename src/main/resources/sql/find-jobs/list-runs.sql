SELECT id, business_date, job_instance_id, job_execution_id, status,
       started_at, completed_at, failure_reason
FROM workspace_search_run
WHERE workspace_id = :workspaceId
ORDER BY id DESC
LIMIT 25
