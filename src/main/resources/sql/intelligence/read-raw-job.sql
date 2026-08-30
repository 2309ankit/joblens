SELECT r.id, r.source, r.external_job_id, r.source_url, r.payload_hash,
       r.raw_payload_json::text AS raw_json
FROM raw_job_posting r
WHERE r.processing_status IN ('NEW', 'FAILED')
  AND r.id > :currentId
  AND (CAST(:workspaceId AS UUID) IS NULL OR EXISTS (
      SELECT 1
      FROM workspace_job_sighting sighting
      WHERE sighting.raw_job_posting_id = r.id
        AND sighting.workspace_id = :workspaceId
  ))
ORDER BY r.id
LIMIT 1
