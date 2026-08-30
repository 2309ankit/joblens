INSERT INTO workspace_resume (
    workspace_id, original_filename, content_type, size_bytes, content_hash, status
) VALUES (
    :workspaceId, :filename, :contentType, :sizeBytes, :contentHash, 'PARSED'
)
ON CONFLICT (workspace_id, content_hash)
DO UPDATE SET original_filename = EXCLUDED.original_filename
RETURNING id
