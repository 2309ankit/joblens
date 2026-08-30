INSERT INTO candidate_profile (
    name, summary, target_roles, target_domains, primary_location
) VALUES (
    :name, :summary, :targetRoles, :targetDomains, :primaryLocation
)
RETURNING id
