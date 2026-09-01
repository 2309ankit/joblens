UPDATE workspace_resume resume
SET status = CASE WHEN :status = 'REVIEW_REQUIRED' THEN 'REVIEW_REQUIRED' ELSE 'PARSED' END,
    validation_message = :validationMessage
FROM workspace_profile_version profile
WHERE profile.id = :profileVersionId
  AND resume.id = profile.resume_id
