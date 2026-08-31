SELECT n.source_url, n.source, profile.source_key
FROM normalized_job n
JOIN raw_job_posting raw ON raw.id = n.raw_job_posting_id
JOIN search_profile profile ON profile.profile_id = raw.search_profile_id
WHERE n.id = :jobId
  AND EXISTS (
    SELECT 1
    FROM workspace_job_sighting sighting
    WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
      AND sighting.workspace_id = :workspaceId
  )
