SELECT id, normalized_job_id, candidate_profile_id, status, status_effective_date,
       applied_on, note, created_at, updated_at
FROM job_application
WHERE id = :id
FOR UPDATE
