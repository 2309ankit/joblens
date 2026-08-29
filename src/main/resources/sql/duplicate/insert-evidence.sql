INSERT INTO duplicate_match_evidence (
    cluster_id, left_job_id, right_job_id, evidence_type, evidence_value
) VALUES (:clusterId, :leftJobId, :rightJobId, :evidenceType, :evidenceValue)
ON CONFLICT (cluster_id, left_job_id, right_job_id, evidence_type) DO NOTHING
