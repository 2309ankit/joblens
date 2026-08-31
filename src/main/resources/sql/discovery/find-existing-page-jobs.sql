SELECT external_job_id, payload_hash
FROM raw_job_posting
WHERE source = :source
  AND external_job_id IN (:externalJobIds)
