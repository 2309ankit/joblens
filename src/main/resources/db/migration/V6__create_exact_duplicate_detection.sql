CREATE TABLE duplicate_cluster (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cluster_key VARCHAR(100) NOT NULL UNIQUE,
    canonical_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    member_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT duplicate_cluster_canonical_un UNIQUE (canonical_job_id),
    CONSTRAINT duplicate_cluster_member_count_chk CHECK (member_count >= 2)
);

CREATE TABLE duplicate_cluster_member (
    cluster_id BIGINT NOT NULL REFERENCES duplicate_cluster(id) ON DELETE CASCADE,
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    is_canonical BOOLEAN NOT NULL DEFAULT FALSE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cluster_id, normalized_job_id),
    CONSTRAINT duplicate_cluster_member_job_un UNIQUE (normalized_job_id)
);

CREATE UNIQUE INDEX duplicate_cluster_one_canonical_idx
    ON duplicate_cluster_member(cluster_id) WHERE is_canonical;

CREATE TABLE duplicate_match_evidence (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cluster_id BIGINT NOT NULL REFERENCES duplicate_cluster(id) ON DELETE CASCADE,
    left_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    right_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    evidence_type VARCHAR(40) NOT NULL,
    evidence_value TEXT NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT duplicate_match_evidence_order_chk CHECK (left_job_id < right_job_id),
    CONSTRAINT duplicate_match_evidence_type_chk CHECK (
        evidence_type IN ('SOURCE_EXTERNAL_ID', 'NORMALIZED_CONTENT_HASH')
    ),
    CONSTRAINT duplicate_match_evidence_value_chk CHECK (length(trim(evidence_value)) > 0),
    CONSTRAINT duplicate_match_evidence_un UNIQUE (
        cluster_id, left_job_id, right_job_id, evidence_type
    )
);

CREATE INDEX duplicate_match_evidence_left_idx ON duplicate_match_evidence(left_job_id);
CREATE INDEX duplicate_match_evidence_right_idx ON duplicate_match_evidence(right_job_id);
