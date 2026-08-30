INSERT INTO workspace_profile_skill (profile_version_id, skill_id)
SELECT :profileVersionId, skill.id
FROM skill
WHERE skill.canonical_name IN (:skills)
  AND EXISTS (
      SELECT 1
      FROM workspace_profile_version profile
      WHERE profile.id = :profileVersionId
        AND profile.workspace_id = :workspaceId
        AND profile.status = 'DRAFT'
  )
ON CONFLICT DO NOTHING
