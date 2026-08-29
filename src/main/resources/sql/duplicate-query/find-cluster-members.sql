SELECT n.id, n.source, n.external_job_id, n.title, n.company,
       n.normalized_content_hash, m.is_canonical, m.added_at
FROM duplicate_cluster_member m
JOIN normalized_job n ON n.id = m.normalized_job_id
WHERE m.cluster_id = :id
ORDER BY m.is_canonical DESC, n.id
