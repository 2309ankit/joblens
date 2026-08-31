SELECT skill.id, skill.canonical_name, skill.category, skill.taxonomy_version,
       skill.canonical_name AS term
FROM skill
WHERE (skill.created_by_workspace_id IS NULL
   OR skill.created_by_workspace_id = :workspaceId)
  AND (skill.taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = skill.taxonomy_version AND release.active
  ))
UNION ALL
SELECT skill.id, skill.canonical_name, skill.category, skill.taxonomy_version,
       alias.alias_name AS term
FROM skill_alias alias
JOIN skill ON skill.id = alias.skill_id
WHERE skill.created_by_workspace_id IS NULL
  AND (skill.taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = skill.taxonomy_version AND release.active
  ))
ORDER BY canonical_name, term
