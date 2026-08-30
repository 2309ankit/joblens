SELECT a.id, a.normalized_job_id, n.title, n.company, a.candidate_profile_id,
       a.status, a.status_effective_date, a.applied_on, a.note,
       a.created_at, a.updated_at
FROM job_application a
JOIN normalized_job n ON n.id = a.normalized_job_id
WHERE (CAST(:status AS VARCHAR) IS NULL OR a.status = :status)
  AND (CAST(:candidateProfileId AS BIGINT) IS NULL OR a.candidate_profile_id = :candidateProfileId)
ORDER BY a.updated_at DESC, a.id DESC
