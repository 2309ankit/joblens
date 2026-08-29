SELECT s.id, s.left_job_id, left_job.title AS left_title,
       s.right_job_id, right_job.title AS right_title,
       s.algorithm_version, s.overall_score, s.title_score, s.description_score,
       s.company_score, s.location_score, s.employment_score,
       s.decision, s.explanation, s.calculated_at
FROM job_similarity s
JOIN normalized_job left_job ON left_job.id = s.left_job_id
JOIN normalized_job right_job ON right_job.id = s.right_job_id
WHERE (CAST(:decision AS VARCHAR) IS NULL OR s.decision = :decision)
  AND (CAST(:minimumScore AS NUMERIC) IS NULL OR s.overall_score >= :minimumScore)
ORDER BY s.overall_score DESC, s.id
