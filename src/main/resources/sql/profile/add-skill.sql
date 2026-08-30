INSERT INTO candidate_skill(candidate_profile_id, skill_id, status, importance) SELECT :profileId, id, 'PRODUCTION', 1.0 FROM skill WHERE lower(canonical_name) = lower(:skill)
