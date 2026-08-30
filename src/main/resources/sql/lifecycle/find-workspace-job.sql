SELECT n.id
FROM normalized_job n
JOIN workspace_job_sighting sighting
  ON sighting.raw_job_posting_id = n.raw_job_posting_id
WHERE n.id = :jobId
  AND sighting.workspace_id = :workspaceId
