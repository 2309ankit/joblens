UPDATE workspace_profile_version
SET status = 'SUPERSEDED', confirmed_at = NULL
WHERE workspace_id = :workspaceId AND status = 'ACTIVE'
