INSERT INTO source_fetch_run (
    source, search_profile_id, status, job_instance_id, job_execution_id
) VALUES (
    :source, :profileId, 'RUNNING', :jobInstanceId, :jobExecutionId
)
ON CONFLICT (job_instance_id, search_profile_id)
DO UPDATE SET status = 'RUNNING',
              completed_at = NULL,
              failure_reason = NULL,
              job_execution_id = EXCLUDED.job_execution_id
RETURNING id, next_page, status
