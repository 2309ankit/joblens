CREATE TABLE nvidia_job_score (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    profile_version_id BIGINT REFERENCES workspace_profile_version(id) ON DELETE CASCADE,
    normalized_content_hash CHAR(64) NOT NULL,
    candidate_fingerprint CHAR(64) NOT NULL,
    model_id VARCHAR(200) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    cache_key CHAR(64) NOT NULL UNIQUE,
    total_score INTEGER NOT NULL,
    confidence NUMERIC(4,3) NOT NULL,
    qualifies_recommended BOOLEAN NOT NULL,
    summary TEXT NOT NULL,
    reasons_json JSONB NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT nvidia_job_score_total_chk CHECK (total_score BETWEEN 0 AND 100),
    CONSTRAINT nvidia_job_score_confidence_chk CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT nvidia_job_score_input_tokens_chk CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT nvidia_job_score_output_tokens_chk CHECK (output_tokens IS NULL OR output_tokens >= 0)
);

CREATE INDEX nvidia_job_score_current_idx
    ON nvidia_job_score(candidate_profile_id, normalized_job_id, created_at DESC);

CREATE TABLE nvidia_job_score_attempt (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    model_id VARCHAR(200) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    cache_key CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT nvidia_job_score_attempt_status_chk CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT nvidia_job_score_attempt_failure_chk CHECK (
        (status = 'SUCCESS' AND failure_reason IS NULL)
        OR (status = 'FAILED' AND failure_reason IS NOT NULL)
    )
);

CREATE INDEX nvidia_job_score_attempt_budget_idx
    ON nvidia_job_score_attempt(model_id, created_at DESC);
