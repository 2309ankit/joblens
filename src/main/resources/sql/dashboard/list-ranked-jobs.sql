SELECT n.id, n.title, n.company, n.location, n.source,
       COALESCE(s.total_score, 0) AS score,
       COALESCE(v.view_count, 0) AS view_count,
       application.id AS application_id,
       application.status AS application_status
FROM normalized_job n
LEFT JOIN job_score s ON s.normalized_job_id = n.id AND s.candidate_profile_id = :candidateProfileId
LEFT JOIN job_view v
  ON v.normalized_job_id = n.id
 AND v.candidate_profile_id = :candidateProfileId
LEFT JOIN job_application application
  ON application.normalized_job_id = n.id
 AND application.candidate_profile_id = :candidateProfileId
WHERE (n.source <> 'ADZUNA'
       OR n.posted_at IS NULL
       OR n.posted_at >= CURRENT_TIMESTAMP - make_interval(days => :maxDaysOld))
  AND EXISTS (
    SELECT 1
    FROM workspace_job_sighting sighting
    JOIN raw_job_posting raw ON raw.id = sighting.raw_job_posting_id
    JOIN search_profile profile ON profile.profile_id = raw.search_profile_id
    WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
      AND sighting.workspace_id = :workspaceId
      AND profile.workspace_id = :workspaceId
      AND profile.active = TRUE
      AND (
          NOT EXISTS (
              SELECT 1
              FROM regexp_split_to_table(lower(profile.keywords), '[^[:alnum:]+#]+') token
              WHERE length(token) >= 3
                AND token NOT IN ('senior', 'junior', 'lead', 'manager', 'executive',
                                  'officer', 'specialist', 'associate')
          )
          OR EXISTS (
              SELECT 1
              FROM regexp_split_to_table(lower(profile.keywords), '[^[:alnum:]+#]+') token
              WHERE length(token) >= 3
                AND token NOT IN ('senior', 'junior', 'lead', 'manager', 'executive',
                                  'officer', 'specialist', 'associate')
                AND lower(n.title) ~ ('(^|[^[:alnum:]])' || token || '([^[:alnum:]]|$)')
          )
      )
)
ORDER BY score DESC, n.id
LIMIT 25
