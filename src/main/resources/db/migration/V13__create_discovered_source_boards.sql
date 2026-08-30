CREATE TABLE discovered_source_board (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    source_key VARCHAR(200) NOT NULL,
    canonical_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DISCOVERED',
    failure_reason TEXT,
    first_discovered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_discovered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_at TIMESTAMPTZ,
    CONSTRAINT discovered_source_board_source_chk CHECK (source IN ('GREENHOUSE')),
    CONSTRAINT discovered_source_board_status_chk
        CHECK (status IN ('DISCOVERED', 'VALIDATED', 'FAILED')),
    CONSTRAINT discovered_source_board_source_key_un UNIQUE (source, source_key)
);

CREATE TABLE workspace_source_board (
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    discovered_source_board_id BIGINT NOT NULL REFERENCES discovered_source_board(id) ON DELETE CASCADE,
    search_definition_id BIGINT NOT NULL REFERENCES workspace_search_definition(id) ON DELETE CASCADE,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, discovered_source_board_id)
);

CREATE INDEX workspace_source_board_definition_idx
    ON workspace_source_board(workspace_id, search_definition_id);
