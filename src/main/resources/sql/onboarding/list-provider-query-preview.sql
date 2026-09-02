SELECT target.country_code,
       target.location,
       role.canonical_name AS role_name,
       query.query_text,
       query.generation_version,
       query.origin,
       query.priority
FROM workspace_search_definition definition
JOIN workspace_search_target target
  ON target.search_definition_id = definition.id
 AND target.active = TRUE
JOIN workspace_search_query query
  ON query.search_target_id = target.id
 AND query.active = TRUE
LEFT JOIN role_catalog role ON role.id = query.role_id
WHERE definition.workspace_id = :workspaceId
  AND definition.active = TRUE
ORDER BY target.priority, query.priority
