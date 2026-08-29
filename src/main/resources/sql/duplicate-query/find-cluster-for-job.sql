SELECT c.id, c.cluster_key, c.canonical_job_id, c.member_count, m.is_canonical
FROM duplicate_cluster_member m
JOIN duplicate_cluster c ON c.id = m.cluster_id
WHERE m.normalized_job_id = :jobId
