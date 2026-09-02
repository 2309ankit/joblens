SELECT category, points, reason_text
FROM job_role_score_reason
WHERE job_role_score_id = :roleScoreId
ORDER BY id
