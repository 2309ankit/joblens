SELECT finding_code, category, severity, message, remediation, evidence, score_deduction
FROM resume_readiness_finding
WHERE assessment_id = :assessmentId
ORDER BY CASE severity WHEN 'REVIEW' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END,
         finding_code
