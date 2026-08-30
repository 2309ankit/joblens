SELECT n.id, n.title, n.company, n.location, n.source,
       COALESCE(s.total_score, 0) AS score,
       COALESCE(v.view_count, 0) AS view_count
FROM normalized_job n
LEFT JOIN job_score s ON s.normalized_job_id = n.id AND s.candidate_profile_id = :candidateProfileId
LEFT JOIN job_view v
  ON v.normalized_job_id = n.id
 AND v.candidate_profile_id = :candidateProfileId
WHERE EXISTS (
    SELECT 1
    FROM workspace_job_sighting sighting
    WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
      AND sighting.workspace_id = :workspaceId
)
ORDER BY score DESC, n.id
LIMIT 25
