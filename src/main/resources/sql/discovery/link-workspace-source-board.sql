INSERT INTO workspace_source_board (
    workspace_id, discovered_source_board_id, search_definition_id
) VALUES (
    :workspaceId, :sourceBoardId, :searchDefinitionId
)
ON CONFLICT (workspace_id, discovered_source_board_id)
DO UPDATE SET search_definition_id = EXCLUDED.search_definition_id,
              last_seen_at = CURRENT_TIMESTAMP
