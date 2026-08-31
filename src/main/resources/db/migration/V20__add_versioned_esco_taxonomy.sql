ALTER TABLE skill
    ALTER COLUMN canonical_name TYPE VARCHAR(300),
    ADD COLUMN taxonomy_source VARCHAR(30) NOT NULL DEFAULT 'JOBLENS',
    ADD COLUMN taxonomy_version VARCHAR(30),
    ADD COLUMN external_uri VARCHAR(500);

ALTER TABLE skill_alias ALTER COLUMN alias_name TYPE VARCHAR(300);
ALTER TABLE skill_alias DROP CONSTRAINT skill_alias_alias_name_key;
CREATE UNIQUE INDEX skill_alias_skill_name_un
    ON skill_alias (skill_id, lower(alias_name));
CREATE INDEX skill_alias_name_idx ON skill_alias (lower(alias_name));
CREATE UNIQUE INDEX skill_external_uri_un
    ON skill (external_uri) WHERE external_uri IS NOT NULL;

ALTER TABLE role_catalog
    ALTER COLUMN canonical_name TYPE VARCHAR(300),
    ADD COLUMN taxonomy_source VARCHAR(30) NOT NULL DEFAULT 'JOBLENS',
    ADD COLUMN taxonomy_version VARCHAR(30),
    ADD COLUMN external_uri VARCHAR(500);

ALTER TABLE role_alias ALTER COLUMN alias_name TYPE VARCHAR(300);
ALTER TABLE role_alias DROP CONSTRAINT role_alias_alias_name_key;
CREATE UNIQUE INDEX role_alias_role_name_un
    ON role_alias (role_id, lower(alias_name));
CREATE INDEX role_alias_name_idx ON role_alias (lower(alias_name));
CREATE UNIQUE INDEX role_external_uri_un
    ON role_catalog (external_uri) WHERE external_uri IS NOT NULL;

CREATE TABLE taxonomy_release (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    version VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    skill_count INTEGER NOT NULL DEFAULT 0,
    occupation_count INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT taxonomy_release_source_version_un UNIQUE (source, version),
    CONSTRAINT taxonomy_release_status_chk CHECK (status IN ('IMPORTING', 'COMPLETE', 'FAILED'))
);

CREATE UNIQUE INDEX taxonomy_release_active_source_un
    ON taxonomy_release (source) WHERE active;

ALTER TABLE workspace_profile_skill_suggestion
    ADD COLUMN evidence_section VARCHAR(30) NOT NULL DEFAULT 'RESUME_BODY',
    ADD COLUMN match_type VARCHAR(30) NOT NULL DEFAULT 'EXACT_CANONICAL',
    ADD COLUMN extractor_version VARCHAR(30) NOT NULL DEFAULT 'literal-v1',
    ADD COLUMN taxonomy_version VARCHAR(30),
    ADD COLUMN start_offset INTEGER,
    ADD COLUMN end_offset INTEGER;

ALTER TABLE workspace_profile_role_suggestion
    ADD COLUMN match_type VARCHAR(30) NOT NULL DEFAULT 'EXACT_CANONICAL',
    ADD COLUMN extractor_version VARCHAR(30) NOT NULL DEFAULT 'literal-v1',
    ADD COLUMN taxonomy_version VARCHAR(30),
    ADD COLUMN start_offset INTEGER,
    ADD COLUMN end_offset INTEGER;

CREATE TABLE workspace_profile_term_suggestion (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_version_id BIGINT NOT NULL
        REFERENCES workspace_profile_version(id) ON DELETE CASCADE,
    term_kind VARCHAR(20) NOT NULL,
    normalized_term VARCHAR(300) NOT NULL,
    evidence_section VARCHAR(30) NOT NULL,
    evidence VARCHAR(300) NOT NULL,
    evidence_strength NUMERIC(4,3) NOT NULL,
    review_state VARCHAR(20) NOT NULL DEFAULT 'SUGGESTED',
    extractor_version VARCHAR(30) NOT NULL,
    priority INTEGER NOT NULL,
    CONSTRAINT profile_term_kind_chk CHECK (term_kind IN ('SKILL', 'ROLE')),
    CONSTRAINT profile_term_strength_chk CHECK (evidence_strength BETWEEN 0 AND 1),
    CONSTRAINT profile_term_review_state_chk CHECK (
        review_state IN ('SUGGESTED', 'AMBIGUOUS', 'REJECTED')
    ),
    CONSTRAINT profile_term_priority_chk CHECK (priority > 0),
    CONSTRAINT profile_term_name_chk CHECK (btrim(normalized_term) <> ''),
    CONSTRAINT profile_term_suggestion_un UNIQUE (
        profile_version_id, term_kind, normalized_term, review_state
    )
);

CREATE INDEX profile_term_suggestion_order_idx
    ON workspace_profile_term_suggestion(profile_version_id, term_kind, priority);
