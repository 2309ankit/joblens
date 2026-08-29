SELECT left_job_id, right_job_id, evidence_type, evidence_value, detected_at
FROM duplicate_match_evidence
WHERE cluster_id = :id
ORDER BY left_job_id, right_job_id, evidence_type
