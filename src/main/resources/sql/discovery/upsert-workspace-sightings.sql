INSERT INTO workspace_job_sighting (
    workspace_id, raw_job_posting_id, search_definition_id
)
SELECT :workspaceId, id, :searchDefinitionId
FROM raw_job_posting
WHERE source = :source
  AND external_job_id IN (:externalJobIds)
ON CONFLICT (workspace_id, raw_job_posting_id)
DO UPDATE SET search_definition_id = EXCLUDED.search_definition_id,
              last_seen_at = CURRENT_TIMESTAMP
