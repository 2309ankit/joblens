package com.ankit.joblens.intelligence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class NvidiaScoringRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public NvidiaScoringRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public List<NvidiaScoringCandidate> shortlist(
      long candidateProfileId, UUID workspaceId, int limit) {
    Map<String, Object> parameters = new java.util.HashMap<>();
    parameters.put("candidateProfileId", candidateProfileId);
    parameters.put("workspaceId", workspaceId);
    parameters.put("limit", limit);
    return jdbc.query(
        """
        SELECT n.id, n.source, n.external_job_id, n.title, n.company, n.location,
               n.description_text, n.employment_type, n.salary_min, n.salary_max,
               n.salary_currency, n.remote_type, n.posted_at, n.source_url,
               n.normalized_content_hash, score.total_score, score.qualifies_recommended,
               owner.profile_version_id
        FROM job_score score
        JOIN normalized_job n ON n.id = score.normalized_job_id
        LEFT JOIN workspace_candidate_profile owner
          ON owner.candidate_profile_id = score.candidate_profile_id
        WHERE score.candidate_profile_id = :candidateProfileId
          AND (CAST(:workspaceId AS UUID) IS NULL OR EXISTS (
              SELECT 1 FROM workspace_job_sighting sighting
              WHERE sighting.raw_job_posting_id = n.raw_job_posting_id
                AND sighting.workspace_id = :workspaceId
          ))
        ORDER BY score.total_score DESC, n.id
        LIMIT :limit
        """,
        parameters,
        NvidiaScoringRepository::candidate);
  }

  public boolean hasCachedScore(String cacheKey) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM nvidia_job_score WHERE cache_key = :cacheKey)",
            Map.of("cacheKey", cacheKey),
            Boolean.class));
  }

  public int callsToday(String modelId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM nvidia_job_score_attempt
            WHERE model_id = :modelId AND created_at >= date_trunc('day', CURRENT_TIMESTAMP)
            """,
            Map.of("modelId", modelId),
            Integer.class);
    return count == null ? 0 : count;
  }

  @Transactional
  public void recordSuccess(
      NvidiaScoringCandidate candidate,
      long candidateProfileId,
      String candidateFingerprint,
      String cacheKey,
      NvidiaRankingProperties properties,
      NvidiaScoreResult result) {
    String reasons;
    try {
      reasons = objectMapper.writeValueAsString(result.reasons());
    } catch (JacksonException exception) {
      throw new IllegalStateException("Could not persist NVIDIA score reasons", exception);
    }
    Map<String, Object> parameters =
        new java.util.HashMap<>(
            Map.ofEntries(
                Map.entry("normalizedJobId", candidate.job().id()),
                Map.entry("candidateProfileId", candidateProfileId),
                Map.entry("normalizedContentHash", candidate.job().contentHash()),
                Map.entry("candidateFingerprint", candidateFingerprint),
                Map.entry("modelId", properties.model()),
                Map.entry("promptVersion", properties.promptVersion()),
                Map.entry("cacheKey", cacheKey),
                Map.entry("totalScore", result.totalScore()),
                Map.entry("confidence", result.confidence()),
                Map.entry("qualifiesRecommended", result.qualifiesRecommended()),
                Map.entry("summary", result.summary()),
                Map.entry("reasons", reasons)));
    parameters.put("profileVersionId", candidate.profileVersionId());
    parameters.put("inputTokens", result.inputTokens());
    parameters.put("outputTokens", result.outputTokens());
    jdbc.update(
        """
        INSERT INTO nvidia_job_score(
            normalized_job_id, candidate_profile_id, profile_version_id, normalized_content_hash,
            candidate_fingerprint, model_id, prompt_version, cache_key, total_score, confidence,
            qualifies_recommended, summary, reasons_json, input_tokens, output_tokens)
        VALUES (:normalizedJobId, :candidateProfileId, :profileVersionId, :normalizedContentHash,
                :candidateFingerprint, :modelId, :promptVersion, :cacheKey, :totalScore,
                :confidence, :qualifiesRecommended, :summary, CAST(:reasons AS JSONB),
                :inputTokens, :outputTokens)
        ON CONFLICT (cache_key) DO NOTHING
        """,
        parameters);
    jdbc.update(
        """
        INSERT INTO nvidia_job_score_attempt(
            normalized_job_id, candidate_profile_id, model_id, prompt_version, cache_key, status,
            input_tokens, output_tokens)
        VALUES (:normalizedJobId, :candidateProfileId, :modelId, :promptVersion, :cacheKey,
                'SUCCESS', :inputTokens, :outputTokens)
        """,
        parameters);
  }

  public void recordFailure(
      NvidiaScoringCandidate candidate,
      long candidateProfileId,
      String cacheKey,
      NvidiaRankingProperties properties,
      String failureReason) {
    jdbc.update(
        """
        INSERT INTO nvidia_job_score_attempt(
            normalized_job_id, candidate_profile_id, model_id, prompt_version, cache_key, status,
            failure_reason)
        VALUES (:normalizedJobId, :candidateProfileId, :modelId, :promptVersion, :cacheKey,
                'FAILED', :failureReason)
        """,
        Map.of(
            "normalizedJobId",
            candidate.job().id(),
            "candidateProfileId",
            candidateProfileId,
            "modelId",
            properties.model(),
            "promptVersion",
            properties.promptVersion(),
            "cacheKey",
            cacheKey,
            "failureReason",
            failureReason));
  }

  private static NvidiaScoringCandidate candidate(ResultSet resultSet, int ignored)
      throws SQLException {
    NormalizedJobView job =
        new NormalizedJobView(
            resultSet.getLong("id"),
            resultSet.getString("source"),
            resultSet.getString("external_job_id"),
            resultSet.getString("title"),
            resultSet.getString("company"),
            resultSet.getString("location"),
            resultSet.getString("description_text"),
            resultSet.getString("employment_type"),
            resultSet.getBigDecimal("salary_min"),
            resultSet.getBigDecimal("salary_max"),
            resultSet.getString("salary_currency"),
            resultSet.getString("remote_type"),
            resultSet.getObject("posted_at", java.time.OffsetDateTime.class),
            resultSet.getString("source_url"),
            resultSet.getString("normalized_content_hash"));
    return new NvidiaScoringCandidate(
        job,
        resultSet.getInt("total_score"),
        resultSet.getBoolean("qualifies_recommended"),
        resultSet.getObject("profile_version_id", Long.class));
  }
}
