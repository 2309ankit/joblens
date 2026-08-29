INSERT INTO duplicate_cluster (cluster_key, canonical_job_id, member_count)
VALUES (:clusterKey, :canonicalJobId, :memberCount)
ON CONFLICT (cluster_key) DO UPDATE SET
    canonical_job_id = EXCLUDED.canonical_job_id,
    member_count = EXCLUDED.member_count,
    updated_at = CASE
        WHEN duplicate_cluster.canonical_job_id <> EXCLUDED.canonical_job_id
          OR duplicate_cluster.member_count <> EXCLUDED.member_count
        THEN CURRENT_TIMESTAMP ELSE duplicate_cluster.updated_at END
RETURNING id
