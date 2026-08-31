INSERT INTO workspace_profile_skill_suggestion (
    profile_version_id, skill_id, matched_term, evidence, confidence,
    evidence_section, match_type, extractor_version, taxonomy_version,
    start_offset, end_offset
) VALUES (
    :profileVersionId, :skillId, :matchedTerm, :evidence, :confidence,
    :evidenceSection, :matchType, :extractorVersion, :taxonomyVersion,
    :startOffset, :endOffset
)
ON CONFLICT (profile_version_id, skill_id) DO UPDATE SET
    matched_term = EXCLUDED.matched_term,
    evidence = EXCLUDED.evidence,
    confidence = EXCLUDED.confidence,
    evidence_section = EXCLUDED.evidence_section,
    match_type = EXCLUDED.match_type,
    extractor_version = EXCLUDED.extractor_version,
    taxonomy_version = EXCLUDED.taxonomy_version,
    start_offset = EXCLUDED.start_offset,
    end_offset = EXCLUDED.end_offset
