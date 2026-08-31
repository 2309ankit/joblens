SELECT skill.id, skill.canonical_name, candidate_skill.status, candidate_skill.importance
FROM candidate_skill
JOIN skill ON skill.id = candidate_skill.skill_id
WHERE candidate_skill.candidate_profile_id = :candidateProfileId
