SELECT id, canonical_name
FROM role_catalog
WHERE (
      lower(canonical_name) = lower(:name)
      OR EXISTS (
          SELECT 1 FROM role_alias alias
          WHERE alias.role_id = role_catalog.id AND lower(alias.alias_name) = lower(:name)
      )
  )
  AND (created_by_workspace_id IS NULL OR created_by_workspace_id = :workspaceId)
  AND (taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = role_catalog.taxonomy_version AND release.active
  ))
ORDER BY created_by_workspace_id NULLS FIRST
LIMIT 1
