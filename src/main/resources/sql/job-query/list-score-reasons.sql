SELECT category, points, reason_text
FROM job_score_reason r
JOIN job_score s ON s.id = r.job_score_id
WHERE s.normalized_job_id = :id
  AND (:candidateProfileId = 0 OR s.candidate_profile_id = :candidateProfileId)
ORDER BY r.id
