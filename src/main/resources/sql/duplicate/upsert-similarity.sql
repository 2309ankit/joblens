INSERT INTO job_similarity (
    left_job_id, right_job_id, algorithm_version, overall_score, title_score,
    description_score, company_score, location_score, employment_score,
    decision, explanation, left_content_hash, right_content_hash
) VALUES (
    :leftJobId, :rightJobId, :algorithmVersion, :overallScore, :titleScore,
    :descriptionScore, :companyScore, :locationScore, :employmentScore,
    :decision, :explanation, :leftContentHash, :rightContentHash
)
ON CONFLICT (left_job_id, right_job_id, algorithm_version) DO UPDATE SET
    overall_score = EXCLUDED.overall_score,
    title_score = EXCLUDED.title_score,
    description_score = EXCLUDED.description_score,
    company_score = EXCLUDED.company_score,
    location_score = EXCLUDED.location_score,
    employment_score = EXCLUDED.employment_score,
    decision = EXCLUDED.decision,
    explanation = EXCLUDED.explanation,
    left_content_hash = EXCLUDED.left_content_hash,
    right_content_hash = EXCLUDED.right_content_hash,
    calculated_at = CASE
        WHEN job_similarity.overall_score IS DISTINCT FROM EXCLUDED.overall_score
          OR job_similarity.title_score IS DISTINCT FROM EXCLUDED.title_score
          OR job_similarity.description_score IS DISTINCT FROM EXCLUDED.description_score
          OR job_similarity.company_score IS DISTINCT FROM EXCLUDED.company_score
          OR job_similarity.location_score IS DISTINCT FROM EXCLUDED.location_score
          OR job_similarity.employment_score IS DISTINCT FROM EXCLUDED.employment_score
          OR job_similarity.decision IS DISTINCT FROM EXCLUDED.decision
          OR job_similarity.explanation IS DISTINCT FROM EXCLUDED.explanation
          OR job_similarity.left_content_hash IS DISTINCT FROM EXCLUDED.left_content_hash
          OR job_similarity.right_content_hash IS DISTINCT FROM EXCLUDED.right_content_hash
        THEN CURRENT_TIMESTAMP ELSE job_similarity.calculated_at END
