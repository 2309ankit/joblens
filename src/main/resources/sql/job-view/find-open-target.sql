SELECT n.source_url
FROM normalized_job n
WHERE n.id = :jobId
  AND EXISTS (
      SELECT 1
      FROM workspace_job_sighting sighting
      WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
        AND sighting.workspace_id = :workspaceId
  )
