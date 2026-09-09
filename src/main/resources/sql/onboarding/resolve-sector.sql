SELECT sector.id, sector.canonical_name
FROM sector_catalog sector
WHERE (sector.created_by_workspace_id IS NULL OR sector.created_by_workspace_id = :workspaceId)
  AND (
      lower(sector.canonical_name) = lower(:name)
      OR EXISTS (
          SELECT 1
          FROM sector_alias alias
          WHERE alias.sector_id = sector.id AND lower(alias.alias_name) = lower(:name)
      )
  )
ORDER BY sector.created_by_workspace_id IS NOT NULL
LIMIT 1
