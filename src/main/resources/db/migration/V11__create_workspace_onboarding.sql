CREATE TABLE workspace (
    id UUID PRIMARY KEY,
    display_name VARCHAR(150) NOT NULL DEFAULT 'My JobLens',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace_resume (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    original_filename TEXT NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    validation_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspace_resume_size_chk CHECK (size_bytes > 0),
    CONSTRAINT workspace_resume_hash_chk CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT workspace_resume_status_chk CHECK (
        status IN ('VALIDATED', 'REJECTED', 'PARSED', 'REVIEW_REQUIRED')
    ),
    CONSTRAINT workspace_resume_identity_un UNIQUE (workspace_id, content_hash)
);

CREATE TABLE workspace_profile_version (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    summary TEXT,
    target_roles TEXT[] NOT NULL DEFAULT '{}',
    target_domains TEXT[] NOT NULL DEFAULT '{}',
    primary_location VARCHAR(150),
    resume_id BIGINT REFERENCES workspace_resume(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT workspace_profile_version_un UNIQUE (workspace_id, version),
    CONSTRAINT workspace_profile_status_chk CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'REJECTED')
    ),
    CONSTRAINT workspace_profile_confirmed_chk CHECK (
        (status = 'ACTIVE' AND confirmed_at IS NOT NULL)
        OR (status <> 'ACTIVE' AND confirmed_at IS NULL)
    )
);

CREATE UNIQUE INDEX workspace_profile_one_active_idx
    ON workspace_profile_version(workspace_id) WHERE status = 'ACTIVE';

CREATE TABLE workspace_candidate_profile (
    workspace_id UUID PRIMARY KEY REFERENCES workspace(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL UNIQUE REFERENCES candidate_profile(id) ON DELETE CASCADE,
    profile_version_id BIGINT REFERENCES workspace_profile_version(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace_profile_skill (
    profile_version_id BIGINT NOT NULL REFERENCES workspace_profile_version(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    importance NUMERIC(5,2) NOT NULL DEFAULT 1.0,
    PRIMARY KEY (profile_version_id, skill_id),
    CONSTRAINT workspace_profile_skill_status_chk CHECK (
        status IN ('PRODUCTION', 'LEARNING', 'DESIRED')
    ),
    CONSTRAINT workspace_profile_skill_importance_chk CHECK (importance > 0)
);

CREATE TABLE workspace_preference (
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    preference_key VARCHAR(80) NOT NULL,
    preference_value TEXT NOT NULL,
    PRIMARY KEY (workspace_id, preference_key)
);

CREATE TABLE workspace_search_definition (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    keywords TEXT NOT NULL,
    location VARCHAR(150) NOT NULL,
    country_code CHAR(2) NOT NULL,
    employment_types TEXT[] NOT NULL DEFAULT '{}',
    enabled_sources TEXT[] NOT NULL DEFAULT '{ADZUNA}',
    max_pages INTEGER NOT NULL DEFAULT 3,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspace_search_definition_name_un UNIQUE (workspace_id, name),
    CONSTRAINT workspace_search_definition_pages_chk CHECK (max_pages BETWEEN 1 AND 20)
);
