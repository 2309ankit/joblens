INSERT INTO taxonomy_release (source, version, status)
VALUES ('ESCO', :version, 'IMPORTING')
ON CONFLICT (source, version) DO UPDATE SET
    status = CASE
        WHEN taxonomy_release.status = 'COMPLETE' THEN 'COMPLETE'
        ELSE 'IMPORTING'
    END
RETURNING id, status
