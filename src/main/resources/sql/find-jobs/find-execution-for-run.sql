SELECT job_execution_id
FROM workspace_search_run
WHERE workspace_id = :workspaceId
  AND id = :runId
