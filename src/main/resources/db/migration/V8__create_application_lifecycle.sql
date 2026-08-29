CREATE TABLE job_application (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    status_effective_date DATE NOT NULL,
    applied_on DATE,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT job_application_identity_un UNIQUE (normalized_job_id, candidate_profile_id),
    CONSTRAINT job_application_status_chk CHECK (
        status IN ('SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER',
                   'ACCEPTED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT job_application_applied_date_chk CHECK (
        status IN ('SAVED', 'WITHDRAWN') OR applied_on IS NOT NULL
    )
);

CREATE INDEX job_application_status_idx
    ON job_application(candidate_profile_id, status, status_effective_date);

CREATE TABLE application_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_application(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    note TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT application_history_from_status_chk CHECK (
        from_status IS NULL OR from_status IN ('SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW',
                                               'OFFER', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT application_history_to_status_chk CHECK (
        to_status IN ('SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER',
                      'ACCEPTED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT application_history_transition_chk CHECK (
        from_status IS NULL OR from_status <> to_status
    )
);

CREATE INDEX application_status_history_application_idx
    ON application_status_history(application_id, id DESC);

CREATE TABLE application_follow_up (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_application(id) ON DELETE CASCADE,
    source_history_id BIGINT NOT NULL REFERENCES application_status_history(id) ON DELETE CASCADE,
    follow_up_type VARCHAR(30) NOT NULL,
    generation_version VARCHAR(30) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    completed_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT application_follow_up_identity_un UNIQUE (
        application_id, source_history_id, follow_up_type, generation_version
    ),
    CONSTRAINT application_follow_up_type_chk CHECK (
        follow_up_type IN ('APPLICATION_CHECK_IN', 'RECRUITER_CHECK_IN',
                           'INTERVIEW_THANK_YOU', 'OFFER_DECISION')
    ),
    CONSTRAINT application_follow_up_status_chk CHECK (
        status IN ('OPEN', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT application_follow_up_completed_chk CHECK (
        (status = 'COMPLETED' AND completed_on IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_on IS NULL)
    )
);

CREATE INDEX application_follow_up_queue_idx
    ON application_follow_up(status, due_date, id);
