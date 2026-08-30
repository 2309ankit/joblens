UPDATE workspace_profile_version
SET status = 'ACTIVE', confirmed_at = CURRENT_TIMESTAMP
WHERE id = :profileVersionId
  AND workspace_id = :workspaceId
  AND status = 'DRAFT'
