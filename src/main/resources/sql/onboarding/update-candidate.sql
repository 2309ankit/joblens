UPDATE candidate_profile
SET summary = :summary,
    target_roles = :targetRoles,
    target_domains = :targetDomains,
    primary_location = :primaryLocation,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :candidateProfileId
