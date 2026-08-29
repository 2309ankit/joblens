SELECT s.id, s.left_job_id, s.right_job_id, s.overall_score, s.decision, s.explanation,
       CASE WHEN s.left_job_id = :jobId THEN s.right_job_id ELSE s.left_job_id END AS matched_job_id
FROM job_similarity s
WHERE s.left_job_id = :jobId OR s.right_job_id = :jobId
ORDER BY s.overall_score DESC, s.id
