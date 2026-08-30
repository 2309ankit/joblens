INSERT INTO workspace_search_definition (
    workspace_id, name, keywords, location, country_code, enabled_sources, max_pages
) VALUES (
    :workspaceId, 'Primary search', :keywords, :location, :countryCode, :enabledSources, :maxPages
)
ON CONFLICT (workspace_id, name)
DO UPDATE SET keywords = EXCLUDED.keywords,
              location = EXCLUDED.location,
              country_code = EXCLUDED.country_code,
              enabled_sources = EXCLUDED.enabled_sources,
              max_pages = EXCLUDED.max_pages,
              updated_at = CURRENT_TIMESTAMP
