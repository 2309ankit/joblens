SELECT preference_key, preference_value
FROM candidate_preference
WHERE candidate_profile_id = :candidateProfileId
