UPDATE source_fetch_run
SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP,
    failure_reason = :reason, job_execution_id = :jobExecutionId
WHERE id = :fetchRunId
