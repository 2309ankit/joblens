CREATE TABLE query_plan_decision (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_execution_id BIGINT NOT NULL REFERENCES batch_job_execution(job_execution_id) ON DELETE CASCADE,
    search_profile_id VARCHAR(50) NOT NULL REFERENCES search_profile(profile_id) ON DELETE CASCADE,
    original_keywords VARCHAR(500) NOT NULL,
    proposed_keywords VARCHAR(500) NOT NULL,
    proposed_max_pages INTEGER NOT NULL,
    applied BOOLEAN NOT NULL,
    rationale VARCHAR(300) NOT NULL,
    model_id VARCHAR(200) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT query_plan_decision_run_profile_un UNIQUE (job_execution_id, search_profile_id),
    CONSTRAINT query_plan_decision_max_pages_chk CHECK (proposed_max_pages BETWEEN 1 AND 20)
);

CREATE TABLE query_plan_attempt (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_execution_id BIGINT NOT NULL REFERENCES batch_job_execution(job_execution_id) ON DELETE CASCADE,
    search_profile_id VARCHAR(50) NOT NULL REFERENCES search_profile(profile_id) ON DELETE CASCADE,
    model_id VARCHAR(200) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT query_plan_attempt_status_chk CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT query_plan_attempt_failure_chk CHECK (
        (status = 'SUCCESS' AND failure_reason IS NULL)
        OR (status = 'FAILED' AND failure_reason IS NOT NULL)
    )
);

CREATE INDEX query_plan_attempt_budget_idx ON query_plan_attempt(model_id, created_at DESC);
