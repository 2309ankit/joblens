SELECT id, cluster_key, canonical_job_id, member_count, created_at, updated_at
FROM duplicate_cluster
WHERE id = :id
