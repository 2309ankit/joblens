SELECT c.id, c.cluster_key, c.canonical_job_id, c.member_count,
       n.title AS canonical_title, c.created_at, c.updated_at
FROM duplicate_cluster c
JOIN normalized_job n ON n.id = c.canonical_job_id
ORDER BY c.id
