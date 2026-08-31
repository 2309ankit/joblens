INSERT INTO workspace_search_definition (
    workspace_id, name, keywords, enabled_sources, greenhouse_boards, max_pages
) VALUES (
    :workspaceId, 'Primary search', :keywords, '{ADZUNA,JOOBLE}', '{}', :maxPages
)
ON CONFLICT (workspace_id, name)
DO UPDATE SET keywords = EXCLUDED.keywords,
              enabled_sources = '{ADZUNA,JOOBLE}',
              greenhouse_boards = '{}',
              max_pages = EXCLUDED.max_pages,
              updated_at = CURRENT_TIMESTAMP
RETURNING id
