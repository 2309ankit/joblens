INSERT INTO workspace_profile_version (
    workspace_id, version, status, summary, primary_location, resume_id
)
SELECT :workspaceId, COALESCE(MAX(version), 0) + 1, 'DRAFT', :summary, 'Singapore', :resumeId
FROM workspace_profile_version
WHERE workspace_id = :workspaceId
ON CONFLICT (workspace_id, resume_id) WHERE status = 'DRAFT'
DO UPDATE SET summary = EXCLUDED.summary
RETURNING id
