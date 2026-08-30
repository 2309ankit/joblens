UPDATE search_profile
SET active = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE workspace_id = :workspaceId
