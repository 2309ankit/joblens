SELECT id, canonical_name
FROM role_catalog
WHERE lower(canonical_name) = lower(:name)
  AND (created_by_workspace_id IS NULL OR created_by_workspace_id = :workspaceId)
ORDER BY created_by_workspace_id NULLS FIRST
LIMIT 1
