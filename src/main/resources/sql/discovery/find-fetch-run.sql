SELECT id, next_page, status
FROM source_fetch_run
WHERE id = :fetchRunId
