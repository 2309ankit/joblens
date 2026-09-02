INSERT INTO search_profile (
    profile_id, source, source_key, keywords, location, include_skills,
    exclude_skills, employment_type, active, workspace_id, search_definition_id, search_target_id,
    max_pages, search_query_id
) VALUES (
    :profileId, :source, :sourceKey, :keywords, :location, '', '', :employmentType,
    TRUE, :workspaceId, :searchDefinitionId, :searchTargetId, :maxPages, :searchQueryId
)
ON CONFLICT (profile_id)
DO UPDATE SET source = EXCLUDED.source,
              source_key = EXCLUDED.source_key,
              keywords = EXCLUDED.keywords,
              location = EXCLUDED.location,
              employment_type = EXCLUDED.employment_type,
              active = TRUE,
              workspace_id = EXCLUDED.workspace_id,
              search_definition_id = EXCLUDED.search_definition_id,
              search_target_id = EXCLUDED.search_target_id,
              search_query_id = EXCLUDED.search_query_id,
              max_pages = EXCLUDED.max_pages,
              updated_at = CURRENT_TIMESTAMP
