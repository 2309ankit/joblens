CREATE TABLE workspace_profile_target_role (
    profile_version_id BIGINT NOT NULL
        REFERENCES workspace_profile_version(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role_catalog(id) ON DELETE CASCADE,
    priority INTEGER NOT NULL,
    selection_source VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_version_id, role_id),
    CONSTRAINT workspace_profile_target_role_priority_un
        UNIQUE (profile_version_id, priority),
    CONSTRAINT workspace_profile_target_role_priority_chk CHECK (priority BETWEEN 1 AND 3),
    CONSTRAINT workspace_profile_target_role_source_chk CHECK (
        selection_source IN ('RESUME_SUGGESTION', 'USER_ADDED')
    )
);

CREATE TABLE candidate_target_role (
    candidate_profile_id BIGINT NOT NULL
        REFERENCES candidate_profile(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role_catalog(id) ON DELETE CASCADE,
    priority INTEGER NOT NULL,
    selection_source VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (candidate_profile_id, role_id),
    CONSTRAINT candidate_target_role_priority_un UNIQUE (candidate_profile_id, priority),
    CONSTRAINT candidate_target_role_priority_chk CHECK (priority BETWEEN 1 AND 3),
    CONSTRAINT candidate_target_role_source_chk CHECK (
        selection_source IN ('RESUME_SUGGESTION', 'USER_ADDED')
    )
);

ALTER TABLE workspace_search_definition
    ADD COLUMN provider_query_override TEXT;

CREATE TABLE workspace_search_query (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    search_definition_id BIGINT NOT NULL
        REFERENCES workspace_search_definition(id) ON DELETE CASCADE,
    search_target_id BIGINT NOT NULL
        REFERENCES workspace_search_target(id) ON DELETE CASCADE,
    role_id BIGINT REFERENCES role_catalog(id) ON DELETE SET NULL,
    priority INTEGER NOT NULL,
    query_text VARCHAR(500) NOT NULL,
    generation_version VARCHAR(40) NOT NULL,
    origin VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspace_search_query_priority_un UNIQUE (search_target_id, priority),
    CONSTRAINT workspace_search_query_priority_chk CHECK (priority BETWEEN 1 AND 3),
    CONSTRAINT workspace_search_query_text_chk CHECK (btrim(query_text) <> ''),
    CONSTRAINT workspace_search_query_origin_chk CHECK (
        origin IN ('GENERATED', 'OVERRIDE', 'LEGACY')
    )
);

CREATE INDEX workspace_search_query_definition_idx
    ON workspace_search_query(search_definition_id, search_target_id, priority)
    WHERE active = TRUE;

ALTER TABLE search_profile
    ADD COLUMN search_query_id BIGINT
        REFERENCES workspace_search_query(id) ON DELETE SET NULL;

CREATE INDEX search_profile_query_idx ON search_profile(search_query_id)
    WHERE search_query_id IS NOT NULL;

INSERT INTO workspace_profile_target_role (
    profile_version_id, role_id, priority, selection_source
)
SELECT profile.id,
       matched_role.id,
       role_value.ordinality::INTEGER,
       CASE WHEN suggestion.role_id IS NULL THEN 'USER_ADDED' ELSE 'RESUME_SUGGESTION' END
FROM workspace_profile_version profile
CROSS JOIN LATERAL unnest(profile.target_roles) WITH ORDINALITY role_value(role_name, ordinality)
JOIN LATERAL (
    SELECT role.id
    FROM role_catalog role
    WHERE lower(role.canonical_name) = lower(role_value.role_name)
      AND (role.created_by_workspace_id IS NULL
           OR role.created_by_workspace_id = profile.workspace_id)
    ORDER BY role.created_by_workspace_id NULLS FIRST
    LIMIT 1
) matched_role ON TRUE
LEFT JOIN workspace_profile_role_suggestion suggestion
  ON suggestion.profile_version_id = profile.id
 AND suggestion.role_id = matched_role.id
WHERE role_value.ordinality <= 3
ON CONFLICT DO NOTHING;

INSERT INTO candidate_target_role (
    candidate_profile_id, role_id, priority, selection_source
)
SELECT owned.candidate_profile_id, target.role_id, target.priority, target.selection_source
FROM workspace_candidate_profile owned
JOIN workspace_profile_target_role target
  ON target.profile_version_id = owned.profile_version_id
ON CONFLICT DO NOTHING;

INSERT INTO workspace_search_query (
    search_definition_id, search_target_id, priority, query_text,
    generation_version, origin
)
SELECT definition.id, target.id, 1, definition.keywords, 'legacy-v1', 'LEGACY'
FROM workspace_search_definition definition
JOIN workspace_search_target target ON target.search_definition_id = definition.id
WHERE target.active = TRUE;

UPDATE search_profile profile
SET search_query_id = query.id
FROM workspace_search_query query
WHERE query.search_target_id = profile.search_target_id
  AND query.priority = 1;
