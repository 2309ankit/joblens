SELECT COALESCE(MAX(history.id), 0)
FROM application_status_history history
JOIN job_application application ON application.id = history.application_id
WHERE CAST(:candidateProfileId AS BIGINT) IS NULL
   OR application.candidate_profile_id = :candidateProfileId
