SELECT n.id, n.title, n.company, n.location, n.description_text, n.employment_type,
       n.salary_min, n.salary_max, n.salary_currency, n.remote_type, n.posted_at,
       n.source_url, COALESCE(s.total_score, 0) AS score
FROM normalized_job n
LEFT JOIN job_score s
  ON s.normalized_job_id = n.id
 AND (:candidateProfileId = 0 OR s.candidate_profile_id = :candidateProfileId)
ORDER BY score DESC, n.id
