INSERT INTO raw_job_posting (
    source, external_job_id, search_profile_id, source_fetch_run_id,
    source_url, payload_hash, raw_payload_json, job_execution_id
) VALUES (
    :source, :externalJobId, :profileId, :fetchRunId,
    :sourceUrl, :payloadHash, CAST(:rawJson AS jsonb), :jobExecutionId
)
ON CONFLICT (source, external_job_id)
DO UPDATE SET search_profile_id = EXCLUDED.search_profile_id,
              source_fetch_run_id = EXCLUDED.source_fetch_run_id,
              source_url = EXCLUDED.source_url,
              last_seen_at = CURRENT_TIMESTAMP,
              raw_payload_json = CASE
                  WHEN raw_job_posting.payload_hash <> EXCLUDED.payload_hash
                  THEN EXCLUDED.raw_payload_json
                  ELSE raw_job_posting.raw_payload_json
              END,
              payload_hash = EXCLUDED.payload_hash,
              job_execution_id = EXCLUDED.job_execution_id,
              processing_status = CASE
                  WHEN raw_job_posting.payload_hash <> EXCLUDED.payload_hash THEN 'NEW'
                  ELSE raw_job_posting.processing_status
              END,
              processing_reason = CASE
                  WHEN raw_job_posting.payload_hash <> EXCLUDED.payload_hash THEN NULL
                  ELSE raw_job_posting.processing_reason
              END,
              processed_at = CASE
                  WHEN raw_job_posting.payload_hash <> EXCLUDED.payload_hash THEN NULL
                  ELSE raw_job_posting.processed_at
              END,
              updated_at = CASE
                  WHEN raw_job_posting.payload_hash <> EXCLUDED.payload_hash THEN CURRENT_TIMESTAMP
                  ELSE raw_job_posting.updated_at
              END
