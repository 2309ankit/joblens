SELECT id, primary_location, target_roles, target_domains
FROM candidate_profile
WHERE id = :candidateProfileId
  AND active = TRUE
