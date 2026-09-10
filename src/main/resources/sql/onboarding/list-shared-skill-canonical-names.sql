SELECT skill.id, skill.canonical_name
FROM skill
WHERE skill.created_by_workspace_id IS NULL
  AND (skill.taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = skill.taxonomy_version AND release.active
  ))
