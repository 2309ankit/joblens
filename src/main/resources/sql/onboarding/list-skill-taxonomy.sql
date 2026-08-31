SELECT skill.id, skill.canonical_name, skill.category, skill.canonical_name AS term
FROM skill
WHERE skill.created_by_workspace_id IS NULL
   OR skill.created_by_workspace_id = :workspaceId
UNION ALL
SELECT skill.id, skill.canonical_name, skill.category, alias.alias_name AS term
FROM skill_alias alias
JOIN skill ON skill.id = alias.skill_id
WHERE skill.created_by_workspace_id IS NULL
ORDER BY canonical_name, term
