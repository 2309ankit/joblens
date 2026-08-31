UPDATE workspace_search_target
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE search_definition_id = :searchDefinitionId
  AND active = TRUE
