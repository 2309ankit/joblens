INSERT INTO workspace_preference (workspace_id, preference_key, preference_value)
VALUES (:workspaceId, :key, :value)
ON CONFLICT (workspace_id, preference_key)
DO UPDATE SET preference_value = EXCLUDED.preference_value
