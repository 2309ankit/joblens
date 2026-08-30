INSERT INTO workspace_search_run (
    workspace_id, candidate_profile_id, business_date, job_instance_id,
    job_execution_id, status, started_at, completed_at, failure_reason
) VALUES (
    :workspaceId, :candidateProfileId, :businessDate, :jobInstanceId,
    :jobExecutionId, :status, :startedAt, :completedAt, :failureReason
)
ON CONFLICT (job_execution_id)
DO UPDATE SET status = EXCLUDED.status,
              completed_at = EXCLUDED.completed_at,
              failure_reason = EXCLUDED.failure_reason
