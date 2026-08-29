SELECT left_job_id, right_job_id, evidence_type, evidence_value
FROM duplicate_match_evidence
WHERE cluster_id = :clusterId
