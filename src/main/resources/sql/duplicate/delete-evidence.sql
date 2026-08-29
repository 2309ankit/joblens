DELETE FROM duplicate_match_evidence
WHERE cluster_id = :clusterId
  AND left_job_id = :leftJobId
  AND right_job_id = :rightJobId
  AND evidence_type = :evidenceType
