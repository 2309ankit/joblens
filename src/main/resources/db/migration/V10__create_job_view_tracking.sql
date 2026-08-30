CREATE TABLE job_view (
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    first_viewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    view_count INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (normalized_job_id, candidate_profile_id),
    CONSTRAINT job_view_count_chk CHECK (view_count > 0),
    CONSTRAINT job_view_dates_chk CHECK (last_viewed_at >= first_viewed_at)
);

CREATE INDEX job_view_candidate_recent_idx
    ON job_view(candidate_profile_id, last_viewed_at DESC);
