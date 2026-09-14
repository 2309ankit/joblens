SELECT status, records_received
FROM workspace_search_source_run
WHERE search_profile_id = :searchProfileId
ORDER BY id DESC
LIMIT 1
