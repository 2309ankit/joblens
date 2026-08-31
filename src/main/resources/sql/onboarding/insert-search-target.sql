INSERT INTO workspace_search_target (
    search_definition_id, country_code, location, priority
) VALUES (
    :searchDefinitionId, :countryCode, :location, :priority
)
ON CONFLICT (search_definition_id, country_code, (lower(location)))
DO UPDATE SET priority = EXCLUDED.priority,
              active = TRUE,
              updated_at = CURRENT_TIMESTAMP
