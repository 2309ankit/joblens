SELECT n.source_url
FROM normalized_job n
WHERE n.id = :jobId
