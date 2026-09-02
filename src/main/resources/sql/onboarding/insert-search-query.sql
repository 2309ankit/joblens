INSERT INTO workspace_search_query (
    search_definition_id, search_target_id, role_id, priority, query_text,
    generation_version, origin
) VALUES (
    :searchDefinitionId, :searchTargetId, :roleId, :priority, :queryText,
    :generationVersion, :origin
)
RETURNING id
