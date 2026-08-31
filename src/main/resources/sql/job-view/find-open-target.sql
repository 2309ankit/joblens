SELECT CASE
           WHEN n.source = 'ADZUNA'
           THEN COALESCE(raw.raw_payload_json->>'redirect_url', raw.source_url, n.source_url)
           ELSE n.source_url
       END AS source_url
FROM normalized_job n
JOIN raw_job_posting raw ON raw.id = n.raw_job_posting_id
WHERE n.id = :jobId
  AND EXISTS (
    SELECT 1
    FROM workspace_job_sighting sighting
    WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
      AND sighting.workspace_id = :workspaceId
  )
