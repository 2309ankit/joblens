INSERT INTO duplicate_cluster_member (cluster_id, normalized_job_id, is_canonical)
VALUES (:clusterId, :jobId, :canonical)
ON CONFLICT (cluster_id, normalized_job_id) DO UPDATE
SET is_canonical = EXCLUDED.is_canonical
