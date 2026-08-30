SELECT f.id, f.application_id, a.normalized_job_id, n.title, n.company,
       f.follow_up_type, f.generation_version, f.due_date,
       f.status, f.completed_on, f.created_at, f.updated_at
FROM application_follow_up f
JOIN job_application a ON a.id = f.application_id
JOIN normalized_job n ON n.id = a.normalized_job_id
WHERE (CAST(:status AS VARCHAR) IS NULL OR f.status = :status)
  AND (CAST(:dueOnOrBefore AS DATE) IS NULL OR f.due_date <= :dueOnOrBefore)
  AND (CAST(:candidateProfileId AS BIGINT) IS NULL OR a.candidate_profile_id = :candidateProfileId)
ORDER BY f.due_date, f.id
