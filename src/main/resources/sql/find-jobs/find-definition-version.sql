SELECT updated_at::text
FROM workspace_search_definition
WHERE workspace_id = :workspaceId
  AND active = TRUE
ORDER BY id
LIMIT 1
