SELECT n.source_url, c.id AS candidate_profile_id
FROM normalized_job n
CROSS JOIN candidate_profile c
WHERE n.id = :jobId
  AND c.name = 'default'
