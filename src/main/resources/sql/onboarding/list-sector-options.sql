SELECT sector.canonical_name,
       sector.category,
       sector.created_by_workspace_id IS NOT NULL AS custom,
       sector.taxonomy_version
FROM sector_catalog sector
WHERE (sector.created_by_workspace_id IS NULL OR sector.created_by_workspace_id = :workspaceId)
  AND (
      :query = ''
      OR lower(sector.canonical_name) LIKE '%' || lower(:query) || '%'
      OR EXISTS (
          SELECT 1
          FROM sector_alias alias
          WHERE alias.sector_id = sector.id
            AND lower(alias.alias_name) LIKE '%' || lower(:query) || '%'
      )
  )
ORDER BY sector.created_by_workspace_id IS NOT NULL, sector.category, sector.canonical_name
LIMIT 50
