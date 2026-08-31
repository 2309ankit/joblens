UPDATE source_fetch_run
SET pages_fetched = pages_fetched + 1,
    records_received = records_received + :recordCount,
    new_records = new_records + :newCount,
    changed_records = changed_records + :changedCount,
    unchanged_records = unchanged_records + :unchangedCount,
    next_page = :nextPage,
    status = 'RUNNING',
    failure_reason = NULL,
    job_execution_id = :jobExecutionId
WHERE id = :fetchRunId
