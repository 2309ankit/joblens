SELECT id, keywords, location, country_code, enabled_sources, greenhouse_boards, max_pages
FROM workspace_search_definition
WHERE workspace_id = :workspaceId AND active = TRUE
ORDER BY id
LIMIT 1
