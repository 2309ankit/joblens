ALTER TABLE normalized_job
    ADD COLUMN provider_source_domain VARCHAR(150);

UPDATE normalized_job job
SET provider_source_domain = raw.raw_payload_json ->> 'source'
FROM raw_job_posting raw
WHERE raw.id = job.raw_job_posting_id
  AND job.source = 'JOOBLE';
