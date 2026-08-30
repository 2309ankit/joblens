SELECT next_page
FROM source_fetch_run
WHERE id = :fetchRunId
FOR UPDATE
