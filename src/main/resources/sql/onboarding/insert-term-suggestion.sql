INSERT INTO workspace_profile_term_suggestion (
    profile_version_id, term_kind, normalized_term, evidence_section, evidence,
    evidence_strength, review_state, extractor_version, priority
) VALUES (
    :profileVersionId, :termKind, :normalizedTerm, :evidenceSection, :evidence,
    :evidenceStrength, :reviewState, :extractorVersion, :priority
)
ON CONFLICT (profile_version_id, term_kind, normalized_term, review_state) DO UPDATE SET
    evidence_section = EXCLUDED.evidence_section,
    evidence = EXCLUDED.evidence,
    evidence_strength = EXCLUDED.evidence_strength,
    extractor_version = EXCLUDED.extractor_version,
    priority = EXCLUDED.priority
