SELECT f.id, f.application_id, a.candidate_profile_id, f.status, f.due_date, f.completed_on
FROM application_follow_up f
JOIN job_application a ON a.id = f.application_id
WHERE f.id = :id
FOR UPDATE OF f
