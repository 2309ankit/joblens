SELECT id, query_text, priority
FROM workspace_search_query
WHERE search_target_id = :searchTargetId
  AND active = TRUE
ORDER BY priority
