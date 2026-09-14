SELECT profile_id, source, source_key, keywords, location, include_skills,
       exclude_skills, employment_type, active, workspace_id, search_definition_id,
       max_pages, search_target_id, exclude_my_careers_future
FROM search_profile
WHERE active = TRUE AND workspace_id = :workspaceId
ORDER BY profile_id
LIMIT :limit
