SELECT preference_key, preference_value
FROM workspace_preference
WHERE workspace_id = :workspaceId
ORDER BY preference_key
