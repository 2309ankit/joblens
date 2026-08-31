CREATE TABLE workspace_search_target (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    search_definition_id BIGINT NOT NULL
        REFERENCES workspace_search_definition(id) ON DELETE CASCADE,
    country_code CHAR(2) NOT NULL,
    location VARCHAR(150) NOT NULL,
    priority INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspace_search_target_country_chk CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT workspace_search_target_location_chk CHECK (btrim(location) <> ''),
    CONSTRAINT workspace_search_target_priority_chk CHECK (priority BETWEEN 1 AND 10)
);

CREATE UNIQUE INDEX workspace_search_target_identity_un
    ON workspace_search_target(search_definition_id, country_code, lower(location));

CREATE INDEX workspace_search_target_active_order_idx
    ON workspace_search_target(search_definition_id, priority)
    WHERE active = TRUE;

INSERT INTO workspace_search_target (search_definition_id, country_code, location, priority)
SELECT id, upper(country_code), location, 1
FROM workspace_search_definition;

ALTER TABLE search_profile
    ADD COLUMN search_target_id BIGINT
        REFERENCES workspace_search_target(id) ON DELETE SET NULL;

UPDATE search_profile profile
SET search_target_id = target.id
FROM workspace_search_target target
WHERE target.search_definition_id = profile.search_definition_id;

CREATE INDEX search_profile_target_idx
    ON search_profile(search_target_id)
    WHERE search_target_id IS NOT NULL;

ALTER TABLE workspace_search_definition
    DROP COLUMN location,
    DROP COLUMN country_code;
