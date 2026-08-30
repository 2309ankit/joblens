UPDATE source_fetch_run
SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP,
    failure_reason = NULL, job_execution_id = :jobExecutionId
WHERE id = :fetchRunId
