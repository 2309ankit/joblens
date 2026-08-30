UPDATE source_fetch_run
SET pages_fetched = pages_fetched + 1,
    records_received = records_received + :recordCount,
    next_page = :nextPage,
    status = 'RUNNING',
    failure_reason = NULL,
    job_execution_id = :jobExecutionId
WHERE id = :fetchRunId
