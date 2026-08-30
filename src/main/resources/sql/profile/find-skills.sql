SELECT s.canonical_name FROM candidate_skill cs JOIN skill s ON s.id = cs.skill_id WHERE cs.candidate_profile_id = :profileId ORDER BY s.canonical_name
