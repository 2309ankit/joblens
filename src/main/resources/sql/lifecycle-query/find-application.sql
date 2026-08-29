SELECT a.id, a.normalized_job_id, n.title, n.company, n.source_url,
       a.candidate_profile_id, c.name AS candidate_name,
       a.status, a.status_effective_date, a.applied_on, a.note,
       a.created_at, a.updated_at
FROM job_application a
JOIN normalized_job n ON n.id = a.normalized_job_id
JOIN candidate_profile c ON c.id = a.candidate_profile_id
WHERE a.id = :id
