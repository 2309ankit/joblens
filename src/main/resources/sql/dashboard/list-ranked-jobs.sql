SELECT n.id, n.title, n.company, n.location, n.source,
       COALESCE(s.total_score, 0) AS score,
       COALESCE(v.view_count, 0) AS view_count
FROM normalized_job n
LEFT JOIN job_score s ON s.normalized_job_id = n.id
LEFT JOIN candidate_profile c ON c.name = 'default'
LEFT JOIN job_view v
  ON v.normalized_job_id = n.id
 AND v.candidate_profile_id = c.id
ORDER BY score DESC, n.id
LIMIT 25
