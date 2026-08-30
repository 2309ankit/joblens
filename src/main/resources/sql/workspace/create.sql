INSERT INTO workspace (id) VALUES (:workspaceId)
ON CONFLICT (id) DO NOTHING
