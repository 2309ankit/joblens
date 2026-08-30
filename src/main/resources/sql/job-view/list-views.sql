SELECT v.normalized_job_id, n.title, n.company, n.source_url,
       v.first_viewed_at, v.last_viewed_at, v.view_count
FROM job_view v
JOIN normalized_job n ON n.id = v.normalized_job_id
JOIN candidate_profile c ON c.id = v.candidate_profile_id
WHERE c.name = 'default'
ORDER BY v.last_viewed_at DESC, v.normalized_job_id
