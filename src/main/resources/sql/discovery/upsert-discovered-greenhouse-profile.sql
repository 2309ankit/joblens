INSERT INTO search_profile (
    profile_id, source, source_key, keywords, location, include_skills,
    exclude_skills, employment_type, active, workspace_id, search_definition_id, max_pages
) VALUES (
    :profileId, 'GREENHOUSE', :sourceKey, :keywords, :location, '', '', :employmentType,
    TRUE, :workspaceId, :searchDefinitionId, 1
)
ON CONFLICT (profile_id)
DO UPDATE SET source_key = EXCLUDED.source_key,
              keywords = EXCLUDED.keywords,
              location = EXCLUDED.location,
              employment_type = EXCLUDED.employment_type,
              active = TRUE,
              workspace_id = EXCLUDED.workspace_id,
              search_definition_id = EXCLUDED.search_definition_id,
              max_pages = 1,
              updated_at = CURRENT_TIMESTAMP
