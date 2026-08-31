INSERT INTO workspace_profile_skill_suggestion (
    profile_version_id, skill_id, matched_term, evidence, confidence
) VALUES (:profileVersionId, :skillId, :matchedTerm, :evidence, :confidence)
ON CONFLICT (profile_version_id, skill_id) DO UPDATE SET
    matched_term = EXCLUDED.matched_term,
    evidence = EXCLUDED.evidence,
    confidence = EXCLUDED.confidence
