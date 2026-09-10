SELECT suggestion.normalized_term, suggestion.term_kind, suggestion.evidence_section,
       suggestion.evidence, suggestion.evidence_strength, suggestion.review_state,
       suggestion.matched_canonical_term
FROM workspace_profile_term_suggestion suggestion
JOIN workspace_profile_version profile ON profile.id = suggestion.profile_version_id
WHERE suggestion.profile_version_id = :profileVersionId
  AND profile.workspace_id = :workspaceId
ORDER BY suggestion.priority, suggestion.normalized_term
