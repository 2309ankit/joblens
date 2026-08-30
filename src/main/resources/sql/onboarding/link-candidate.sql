INSERT INTO workspace_candidate_profile (
    workspace_id, candidate_profile_id, profile_version_id
) VALUES (
    :workspaceId, :candidateProfileId, :profileVersionId
)
ON CONFLICT (workspace_id)
DO UPDATE SET candidate_profile_id = EXCLUDED.candidate_profile_id,
              profile_version_id = EXCLUDED.profile_version_id,
              updated_at = CURRENT_TIMESTAMP
