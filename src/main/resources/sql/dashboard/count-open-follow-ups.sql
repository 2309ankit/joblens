SELECT count(*) FROM application_follow_up f JOIN job_application a ON a.id = f.application_id WHERE a.candidate_profile_id = :candidateProfileId AND f.status = 'OPEN'
