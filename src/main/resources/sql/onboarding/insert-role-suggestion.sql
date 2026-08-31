INSERT INTO workspace_profile_role_suggestion (
    profile_version_id, role_id, evidence_source, evidence, confidence, priority,
    match_type, extractor_version, taxonomy_version, start_offset, end_offset
) VALUES (
    :profileVersionId, :roleId, :evidenceSource, :evidence, :confidence, :priority,
    :matchType, :extractorVersion, :taxonomyVersion, :startOffset, :endOffset
)
ON CONFLICT (profile_version_id, role_id) DO UPDATE SET
    evidence_source = EXCLUDED.evidence_source,
    evidence = EXCLUDED.evidence,
    confidence = EXCLUDED.confidence,
    priority = EXCLUDED.priority,
    match_type = EXCLUDED.match_type,
    extractor_version = EXCLUDED.extractor_version,
    taxonomy_version = EXCLUDED.taxonomy_version,
    start_offset = EXCLUDED.start_offset,
    end_offset = EXCLUDED.end_offset
