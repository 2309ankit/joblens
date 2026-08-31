SELECT canonical_name, category, created_by_workspace_id IS NOT NULL AS custom
FROM skill
WHERE (created_by_workspace_id IS NULL OR created_by_workspace_id = :workspaceId)
  AND (taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = skill.taxonomy_version AND release.active
  ))
  AND (:query = '' OR lower(canonical_name) LIKE '%' || lower(:query) || '%')
ORDER BY custom, category, canonical_name
LIMIT 100
