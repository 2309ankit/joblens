SELECT n.id, n.source, n.external_job_id, n.title, n.company, n.location,
       n.description_text, n.employment_type, n.salary_min, n.salary_max,
       n.salary_currency, n.remote_type, n.posted_at, n.source_url,
       n.normalized_content_hash
FROM normalized_job n
WHERE n.id > :currentId
  AND (CAST(:onlyUnextracted AS BOOLEAN) = FALSE
       OR n.skill_extraction_hash IS DISTINCT FROM n.normalized_content_hash)
  AND (CAST(:workspaceId AS UUID) IS NULL OR EXISTS (
      SELECT 1
      FROM workspace_job_sighting sighting
      WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
        AND sighting.workspace_id = :workspaceId
  ))
ORDER BY n.id
LIMIT 1
