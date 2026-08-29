CREATE TABLE job_similarity (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    left_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    right_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    algorithm_version VARCHAR(40) NOT NULL,
    overall_score NUMERIC(5, 2) NOT NULL,
    title_score NUMERIC(5, 2) NOT NULL,
    description_score NUMERIC(5, 2),
    company_score NUMERIC(5, 2),
    location_score NUMERIC(5, 2),
    employment_score NUMERIC(5, 2),
    decision VARCHAR(30) NOT NULL,
    explanation TEXT NOT NULL,
    left_content_hash CHAR(64) NOT NULL,
    right_content_hash CHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT job_similarity_pair_order_chk CHECK (left_job_id < right_job_id),
    CONSTRAINT job_similarity_score_chk CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT job_similarity_title_score_chk CHECK (title_score BETWEEN 0 AND 100),
    CONSTRAINT job_similarity_description_score_chk CHECK (
        description_score IS NULL OR description_score BETWEEN 0 AND 100
    ),
    CONSTRAINT job_similarity_company_score_chk CHECK (
        company_score IS NULL OR company_score BETWEEN 0 AND 100
    ),
    CONSTRAINT job_similarity_location_score_chk CHECK (
        location_score IS NULL OR location_score BETWEEN 0 AND 100
    ),
    CONSTRAINT job_similarity_employment_score_chk CHECK (
        employment_score IS NULL OR employment_score BETWEEN 0 AND 100
    ),
    CONSTRAINT job_similarity_decision_chk CHECK (
        decision IN ('POSSIBLE_DUPLICATE', 'LIKELY_DUPLICATE')
    ),
    CONSTRAINT job_similarity_explanation_chk CHECK (length(trim(explanation)) > 0),
    CONSTRAINT job_similarity_hashes_chk CHECK (
        left_content_hash ~ '^[0-9a-f]{64}$' AND right_content_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT job_similarity_pair_version_un UNIQUE (left_job_id, right_job_id, algorithm_version)
);

CREATE INDEX job_similarity_score_idx
    ON job_similarity(algorithm_version, overall_score DESC);
CREATE INDEX job_similarity_right_job_idx ON job_similarity(right_job_id);
