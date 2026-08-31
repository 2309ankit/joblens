SELECT definition.updated_at::text || ':' ||
       COALESCE(string_agg(
           target.country_code || ':' || target.location || ':' || target.priority,
           '|' ORDER BY target.priority
       ), '')
FROM workspace_search_definition definition
LEFT JOIN workspace_search_target target
       ON target.search_definition_id = definition.id
      AND target.active = TRUE
WHERE definition.workspace_id = :workspaceId
  AND definition.active = TRUE
GROUP BY definition.id, definition.updated_at
ORDER BY definition.id
LIMIT 1
