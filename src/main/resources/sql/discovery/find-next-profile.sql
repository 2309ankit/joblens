SELECT profile_id, source, source_key, keywords, location, include_skills,
       exclude_skills, employment_type, active, workspace_id, search_definition_id,
       max_pages, search_target_id, exclude_my_careers_future
FROM search_profile
WHERE active = TRUE
  AND (CAST(:workspaceId AS UUID) IS NULL OR workspace_id = :workspaceId)
  AND (CAST(:requestedProfileId AS VARCHAR) IS NULL OR profile_id = :requestedProfileId)
  AND (CAST(:requestedProfileId AS VARCHAR) IS NOT NULL
       OR CAST(:lastCompletedProfileId AS VARCHAR) IS NULL
       OR profile_id > :lastCompletedProfileId)
ORDER BY profile_id
LIMIT 1
