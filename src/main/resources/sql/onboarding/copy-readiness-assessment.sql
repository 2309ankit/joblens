WITH copied AS (
    INSERT INTO resume_readiness_assessment (
        profile_version_id, assessment_version, status, score, content_type,
        extracted_character_count, word_count, acknowledged_at
    )
    SELECT :draftProfileVersionId, assessment_version, status, score, content_type,
           extracted_character_count, word_count, acknowledged_at
    FROM resume_readiness_assessment
    WHERE profile_version_id = :sourceProfileVersionId
    ON CONFLICT (profile_version_id) DO NOTHING
    RETURNING id
)
INSERT INTO resume_readiness_finding (
    assessment_id, finding_code, category, severity, message, remediation,
    evidence, score_deduction
)
SELECT copied.id, finding.finding_code, finding.category, finding.severity,
       finding.message, finding.remediation, finding.evidence, finding.score_deduction
FROM copied
JOIN resume_readiness_assessment source
  ON source.profile_version_id = :sourceProfileVersionId
JOIN resume_readiness_finding finding ON finding.assessment_id = source.id
ON CONFLICT DO NOTHING
