ALTER TABLE workspace_search_target
    DROP CONSTRAINT workspace_search_target_location_chk;

ALTER TABLE workspace_search_target
    ADD CONSTRAINT workspace_search_target_location_chk
        CHECK (length(btrim(location)) <= 150);

ALTER TABLE workspace_profile_role_suggestion
    DROP CONSTRAINT profile_role_suggestion_source_chk;

ALTER TABLE workspace_profile_role_suggestion
    ADD CONSTRAINT profile_role_suggestion_source_chk CHECK (
        evidence_source IN (
            'RESUME_HEADLINE', 'PROFESSIONAL_SUMMARY', 'RECENT_EXPERIENCE', 'RESUME_BODY'
        )
    );

CREATE TABLE sector_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    canonical_name VARCHAR(100) NOT NULL,
    category VARCHAR(40) NOT NULL,
    taxonomy_source VARCHAR(30) NOT NULL DEFAULT 'JOBLENS',
    taxonomy_version VARCHAR(30),
    created_by_workspace_id UUID REFERENCES workspace(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sector_catalog_name_chk CHECK (btrim(canonical_name) <> '')
);

CREATE UNIQUE INDEX sector_catalog_global_name_un
    ON sector_catalog (lower(canonical_name)) WHERE created_by_workspace_id IS NULL;
CREATE UNIQUE INDEX sector_catalog_workspace_name_un
    ON sector_catalog (created_by_workspace_id, lower(canonical_name))
    WHERE created_by_workspace_id IS NOT NULL;
CREATE INDEX sector_catalog_category_name_idx ON sector_catalog(category, canonical_name);

CREATE TABLE sector_alias (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alias_name VARCHAR(100) NOT NULL,
    sector_id BIGINT NOT NULL REFERENCES sector_catalog(id) ON DELETE CASCADE,
    CONSTRAINT sector_alias_name_chk CHECK (btrim(alias_name) <> '')
);

CREATE UNIQUE INDEX sector_alias_sector_name_un
    ON sector_alias (sector_id, lower(alias_name));
CREATE INDEX sector_alias_name_idx ON sector_alias(lower(alias_name));

INSERT INTO sector_catalog (canonical_name, category, taxonomy_version) VALUES
    ('Technology', 'DIGITAL', 'joblens-sector-v1'),
    ('Financial Services', 'BUSINESS', 'joblens-sector-v1'),
    ('Healthcare', 'PUBLIC_AND_CARE', 'joblens-sector-v1'),
    ('Retail and E-commerce', 'CONSUMER', 'joblens-sector-v1'),
    ('Education', 'PUBLIC_AND_CARE', 'joblens-sector-v1'),
    ('Government and Public Sector', 'PUBLIC_AND_CARE', 'joblens-sector-v1'),
    ('Professional Services', 'BUSINESS', 'joblens-sector-v1'),
    ('Manufacturing', 'INDUSTRIAL', 'joblens-sector-v1'),
    ('Energy and Utilities', 'INDUSTRIAL', 'joblens-sector-v1'),
    ('Transport and Logistics', 'INDUSTRIAL', 'joblens-sector-v1'),
    ('Telecommunications', 'DIGITAL', 'joblens-sector-v1'),
    ('Media and Entertainment', 'CONSUMER', 'joblens-sector-v1'),
    ('Travel and Hospitality', 'CONSUMER', 'joblens-sector-v1'),
    ('Real Estate and Construction', 'INDUSTRIAL', 'joblens-sector-v1'),
    ('Nonprofit and Social Impact', 'PUBLIC_AND_CARE', 'joblens-sector-v1');

INSERT INTO sector_alias (alias_name, sector_id)
SELECT value.alias_name, sector.id
FROM (VALUES
    ('Software', 'Technology'),
    ('Information Technology', 'Technology'),
    ('IT', 'Technology'),
    ('Banking', 'Financial Services'),
    ('Fintech', 'Financial Services'),
    ('Health Care', 'Healthcare'),
    ('Medical', 'Healthcare'),
    ('Retail', 'Retail and E-commerce'),
    ('Ecommerce', 'Retail and E-commerce'),
    ('Public Sector', 'Government and Public Sector'),
    ('Consulting', 'Professional Services'),
    ('Logistics', 'Transport and Logistics'),
    ('Telecom', 'Telecommunications'),
    ('Hospitality', 'Travel and Hospitality'),
    ('Construction', 'Real Estate and Construction'),
    ('NGO', 'Nonprofit and Social Impact')
) value(alias_name, canonical_name)
JOIN sector_catalog sector ON sector.canonical_name = value.canonical_name;

INSERT INTO role_alias (alias_name, role_id)
SELECT value.alias_name, role.id
FROM (VALUES
    ('Front-end Developer', 'Frontend Engineer'),
    ('Front-end Engineer', 'Frontend Engineer')
) value(alias_name, canonical_name)
JOIN role_catalog role ON role.canonical_name = value.canonical_name
ON CONFLICT DO NOTHING;
