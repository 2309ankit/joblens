-- Resume upload previously inserted a new workspace_profile_version DRAFT row on every
-- call, even for the same resume (double submit, retry, or resubmission). Collapse any
-- existing duplicates before enforcing uniqueness going forward.
DELETE FROM workspace_profile_version dup
WHERE dup.status = 'DRAFT'
  AND EXISTS (
      SELECT 1
      FROM workspace_profile_version keep
      WHERE keep.workspace_id = dup.workspace_id
        AND keep.resume_id = dup.resume_id
        AND keep.status = 'DRAFT'
        AND keep.id > dup.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM workspace_candidate_profile wcp
      WHERE wcp.profile_version_id = dup.id
  );

CREATE UNIQUE INDEX workspace_profile_one_draft_per_resume_idx
    ON workspace_profile_version(workspace_id, resume_id)
    WHERE status = 'DRAFT';
