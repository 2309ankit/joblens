SELECT n.*, COALESCE(s.total_score, 0) AS score,
       s.technical_score, s.domain_score, s.seniority_score, s.location_score,
       s.employment_score, s.salary_score, s.freshness_score
FROM normalized_job n
LEFT JOIN job_score s
  ON s.normalized_job_id = n.id
 AND (:candidateProfileId = 0 OR s.candidate_profile_id = :candidateProfileId)
WHERE n.id = :id
  AND (CAST(:workspaceId AS UUID) IS NULL OR EXISTS (
      SELECT 1
      FROM workspace_job_sighting sighting
      WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
        AND sighting.workspace_id = :workspaceId
  ))
