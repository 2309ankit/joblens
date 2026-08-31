SELECT skill.canonical_name, skill.category, suggestion.matched_term,
       suggestion.evidence, suggestion.confidence
FROM workspace_profile_skill_suggestion suggestion
JOIN skill ON skill.id = suggestion.skill_id
JOIN workspace_profile_version profile ON profile.id = suggestion.profile_version_id
WHERE suggestion.profile_version_id = :profileVersionId
  AND profile.workspace_id = :workspaceId
ORDER BY skill.canonical_name
