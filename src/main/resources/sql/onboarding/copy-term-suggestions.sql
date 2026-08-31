INSERT INTO workspace_profile_term_suggestion (
    profile_version_id, term_kind, normalized_term, evidence_section, evidence,
    evidence_strength, review_state, extractor_version, priority
)
SELECT :draftProfileVersionId, term_kind, normalized_term, evidence_section, evidence,
       evidence_strength, review_state, extractor_version, priority
FROM workspace_profile_term_suggestion
WHERE profile_version_id = :sourceProfileVersionId
ON CONFLICT (profile_version_id, term_kind, normalized_term, review_state) DO NOTHING
