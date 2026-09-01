INSERT INTO resume_readiness_finding (
    assessment_id, finding_code, category, severity, message, remediation,
    evidence, score_deduction
) VALUES (
    :assessmentId, :code, :category, :severity, :message, :remediation,
    :evidence, :scoreDeduction
)
ON CONFLICT (assessment_id, finding_code) DO UPDATE SET
    category = EXCLUDED.category,
    severity = EXCLUDED.severity,
    message = EXCLUDED.message,
    remediation = EXCLUDED.remediation,
    evidence = EXCLUDED.evidence,
    score_deduction = EXCLUDED.score_deduction
