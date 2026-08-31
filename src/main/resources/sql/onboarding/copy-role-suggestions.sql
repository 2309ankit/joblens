INSERT INTO workspace_profile_role_suggestion (
    profile_version_id, role_id, evidence_source, evidence, confidence, priority
)
SELECT :draftProfileVersionId, role_id, evidence_source, evidence, confidence, priority
FROM workspace_profile_role_suggestion
WHERE profile_version_id = :sourceProfileVersionId
ON CONFLICT DO NOTHING
