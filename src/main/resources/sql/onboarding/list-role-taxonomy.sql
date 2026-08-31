SELECT role.id, role.canonical_name, role.category, role.canonical_name AS term
FROM role_catalog role
WHERE role.created_by_workspace_id IS NULL
   OR role.created_by_workspace_id = :workspaceId
UNION ALL
SELECT role.id, role.canonical_name, role.category, alias.alias_name AS term
FROM role_alias alias
JOIN role_catalog role ON role.id = alias.role_id
WHERE role.created_by_workspace_id IS NULL
ORDER BY canonical_name, term
