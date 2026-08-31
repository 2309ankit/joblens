INSERT INTO workspace_profile_role_suggestion (
    profile_version_id, role_id, evidence_source, evidence, confidence, priority
) VALUES (:profileVersionId, :roleId, :evidenceSource, :evidence, :confidence, :priority)
ON CONFLICT (profile_version_id, role_id) DO UPDATE SET
    evidence_source = EXCLUDED.evidence_source,
    evidence = EXCLUDED.evidence,
    confidence = EXCLUDED.confidence,
    priority = EXCLUDED.priority
