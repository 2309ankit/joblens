INSERT INTO workspace_profile_skill_suggestion (
    profile_version_id, skill_id, matched_term, evidence, confidence
)
SELECT :draftProfileVersionId, skill_id, matched_term, evidence, confidence
FROM workspace_profile_skill_suggestion
WHERE profile_version_id = :sourceProfileVersionId
ON CONFLICT DO NOTHING
