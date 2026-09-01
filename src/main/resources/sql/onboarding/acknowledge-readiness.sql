UPDATE resume_readiness_assessment assessment
SET acknowledged_at = COALESCE(acknowledged_at, CURRENT_TIMESTAMP)
FROM workspace_profile_version profile
WHERE assessment.profile_version_id = profile.id
  AND profile.workspace_id = :workspaceId
  AND profile.id = :profileVersionId
  AND assessment.status = 'REVIEW_REQUIRED'
