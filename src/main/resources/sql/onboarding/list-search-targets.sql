SELECT id, country_code, location, priority
FROM workspace_search_target
WHERE search_definition_id = :searchDefinitionId
  AND active = TRUE
ORDER BY priority, id
