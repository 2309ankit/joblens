DELETE FROM workspace_profile_skill
WHERE profile_version_id = :profileVersionId
  AND EXISTS (
      SELECT 1
      FROM workspace_profile_version profile
      WHERE profile.id = :profileVersionId
        AND profile.workspace_id = :workspaceId
        AND profile.status = 'DRAFT'
  )
