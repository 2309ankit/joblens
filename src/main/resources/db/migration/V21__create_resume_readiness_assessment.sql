CREATE TABLE resume_readiness_assessment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_version_id BIGINT NOT NULL UNIQUE
        REFERENCES workspace_profile_version(id) ON DELETE CASCADE,
    assessment_version VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    score INTEGER NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    extracted_character_count INTEGER NOT NULL,
    word_count INTEGER NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT resume_readiness_status_chk CHECK (status IN ('READY', 'REVIEW_REQUIRED')),
    CONSTRAINT resume_readiness_score_chk CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT resume_readiness_counts_chk CHECK (
        extracted_character_count > 0 AND word_count > 0
    )
);

CREATE TABLE resume_readiness_finding (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_id BIGINT NOT NULL
        REFERENCES resume_readiness_assessment(id) ON DELETE CASCADE,
    finding_code VARCHAR(60) NOT NULL,
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(300) NOT NULL,
    remediation VARCHAR(500) NOT NULL,
    evidence VARCHAR(300),
    score_deduction INTEGER NOT NULL,
    CONSTRAINT resume_readiness_finding_un UNIQUE (assessment_id, finding_code),
    CONSTRAINT resume_readiness_severity_chk CHECK (
        severity IN ('PASS', 'WARNING', 'REVIEW')
    ),
    CONSTRAINT resume_readiness_deduction_chk CHECK (score_deduction BETWEEN 0 AND 100)
);

CREATE INDEX resume_readiness_finding_order_idx
    ON resume_readiness_finding(assessment_id, severity DESC, finding_code);
