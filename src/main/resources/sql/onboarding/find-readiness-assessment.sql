SELECT assessment.id, assessment.profile_version_id, assessment.assessment_version,
       assessment.status, assessment.score, assessment.content_type,
       assessment.extracted_character_count, assessment.word_count,
       assessment.acknowledged_at, assessment.created_at
FROM resume_readiness_assessment assessment
JOIN workspace_profile_version profile ON profile.id = assessment.profile_version_id
WHERE profile.workspace_id = :workspaceId
  AND profile.id = :profileVersionId
