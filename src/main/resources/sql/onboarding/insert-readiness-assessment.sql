INSERT INTO resume_readiness_assessment (
    profile_version_id, assessment_version, status, score, content_type,
    extracted_character_count, word_count
) VALUES (
    :profileVersionId, :assessmentVersion, :status, :score, :contentType,
    :extractedCharacterCount, :wordCount
)
ON CONFLICT (profile_version_id) DO UPDATE SET
    assessment_version = EXCLUDED.assessment_version,
    status = EXCLUDED.status,
    score = EXCLUDED.score,
    content_type = EXCLUDED.content_type,
    extracted_character_count = EXCLUDED.extracted_character_count,
    word_count = EXCLUDED.word_count,
    acknowledged_at = NULL,
    created_at = CURRENT_TIMESTAMP
RETURNING id
