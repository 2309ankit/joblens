SELECT id, business_date,
       CASE
           WHEN status IN ('STARTING', 'STARTED', 'STOPPING') THEN 'ACTIVE'
           WHEN status IN ('STOPPED', 'ABANDONED', 'UNKNOWN') THEN 'FAILED'
           ELSE status
       END AS status,
       started_at, completed_at, failure_reason
FROM workspace_search_run
WHERE workspace_id = :workspaceId
ORDER BY id DESC
LIMIT 25
