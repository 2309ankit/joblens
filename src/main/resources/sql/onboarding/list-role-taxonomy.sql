SELECT role.id, role.canonical_name, role.category, role.taxonomy_version,
       role.canonical_name AS term
FROM role_catalog role
WHERE (role.created_by_workspace_id IS NULL
   OR role.created_by_workspace_id = :workspaceId)
  AND (role.taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = role.taxonomy_version AND release.active
  ))
UNION ALL
SELECT role.id, role.canonical_name, role.category, role.taxonomy_version,
       alias.alias_name AS term
FROM role_alias alias
JOIN role_catalog role ON role.id = alias.role_id
WHERE role.created_by_workspace_id IS NULL
  AND (role.taxonomy_source <> 'ESCO' OR EXISTS (
      SELECT 1 FROM taxonomy_release release
      WHERE release.source = 'ESCO' AND release.version = role.taxonomy_version AND release.active
  ))
ORDER BY canonical_name, term
