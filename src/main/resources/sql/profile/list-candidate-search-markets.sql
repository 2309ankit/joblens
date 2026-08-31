SELECT DISTINCT target.country_code, target.location
FROM search_profile profile
JOIN workspace_candidate_profile owner
  ON owner.workspace_id = profile.workspace_id
JOIN workspace_search_target target
  ON target.id = profile.search_target_id
WHERE owner.candidate_profile_id = :candidateProfileId
  AND profile.source = 'ADZUNA'
  AND profile.active = TRUE
ORDER BY target.location
