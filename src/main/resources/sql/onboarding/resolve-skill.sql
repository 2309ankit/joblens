SELECT id, canonical_name
FROM skill
WHERE lower(canonical_name) = lower(:name)
  AND (created_by_workspace_id IS NULL OR created_by_workspace_id = :workspaceId)
  AND (taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = skill.taxonomy_version AND release.active
  ))
ORDER BY created_by_workspace_id NULLS FIRST
LIMIT 1
