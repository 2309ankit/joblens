INSERT INTO skill (canonical_name, category, created_by_workspace_id)
VALUES (:name, 'USER_DEFINED', :workspaceId)
ON CONFLICT (created_by_workspace_id, lower(canonical_name))
    WHERE created_by_workspace_id IS NOT NULL
DO UPDATE SET canonical_name = skill.canonical_name
RETURNING id, canonical_name
