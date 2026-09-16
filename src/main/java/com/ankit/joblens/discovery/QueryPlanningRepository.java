package com.ankit.joblens.discovery;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class QueryPlanningRepository {
  private static final String SEARCH_PROFILE_ID = "searchProfileId";
  private static final String MODEL_ID = "modelId";
  private static final String JOB_EXECUTION_ID = "jobExecutionId";

  private final NamedParameterJdbcTemplate jdbc;

  public QueryPlanningRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<SearchProfile> activeProfiles(UUID workspaceId, int limit) {
    return jdbc.query(
        load("sql/discovery/list-active-profiles-for-workspace.sql"),
        new MapSqlParameterSource("workspaceId", workspaceId).addValue("limit", limit),
        (resultSet, rowNumber) ->
            new SearchProfile(
                resultSet.getString("profile_id"),
                resultSet.getString("source"),
                resultSet.getString("source_key"),
                resultSet.getString("keywords"),
                resultSet.getString("location"),
                resultSet.getString("include_skills"),
                resultSet.getString("exclude_skills"),
                resultSet.getString("employment_type"),
                resultSet.getBoolean("active"),
                resultSet.getObject("workspace_id", UUID.class),
                resultSet.getObject("search_definition_id", Long.class),
                resultSet.getObject("max_pages", Integer.class),
                resultSet.getObject("search_target_id", Long.class),
                resultSet.getBoolean("exclude_my_careers_future")));
  }

  public LatestOutcome latestOutcome(String searchProfileId) {
    List<LatestOutcome> outcomes =
        jdbc.query(
            load("sql/discovery/find-latest-source-run-outcome.sql"),
            Map.of(SEARCH_PROFILE_ID, searchProfileId),
            (resultSet, rowNumber) ->
                new LatestOutcome(
                    resultSet.getString("status"), resultSet.getInt("records_received")));
    return outcomes.isEmpty() ? null : outcomes.getFirst();
  }

  public int callsToday(String modelId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM query_plan_attempt
            WHERE model_id = :modelId AND created_at >= date_trunc('day', CURRENT_TIMESTAMP)
            """,
            Map.of(MODEL_ID, modelId),
            Integer.class);
    return count == null ? 0 : count;
  }

  public void recordSuccess(
      long jobExecutionId,
      String searchProfileId,
      String originalKeywords,
      LlmQueryPlanningProperties properties,
      QueryPlanResult result) {
    Map<String, Object> parameters =
        new java.util.HashMap<>(
            Map.of(
                JOB_EXECUTION_ID,
                jobExecutionId,
                SEARCH_PROFILE_ID,
                searchProfileId,
                "originalKeywords",
                originalKeywords,
                "proposedKeywords",
                result.queryText(),
                "proposedMaxPages",
                result.maxPages(),
                "rationale",
                result.rationale(),
                MODEL_ID,
                properties.model(),
                "promptVersion",
                properties.promptVersion()));
    parameters.put("inputTokens", result.inputTokens());
    parameters.put("outputTokens", result.outputTokens());
    jdbc.update(
        """
        INSERT INTO query_plan_decision(
            job_execution_id, search_profile_id, original_keywords, proposed_keywords,
            proposed_max_pages, applied, rationale, model_id, prompt_version)
        VALUES (:jobExecutionId, :searchProfileId, :originalKeywords, :proposedKeywords,
                :proposedMaxPages, TRUE, :rationale, :modelId, :promptVersion)
        ON CONFLICT (job_execution_id, search_profile_id) DO NOTHING
        """,
        parameters);
    jdbc.update(
        """
        INSERT INTO query_plan_attempt(
            job_execution_id, search_profile_id, model_id, prompt_version, status,
            input_tokens, output_tokens)
        VALUES (:jobExecutionId, :searchProfileId, :modelId, :promptVersion, 'SUCCESS',
                :inputTokens, :outputTokens)
        """,
        parameters);
  }

  public void recordFailure(
      long jobExecutionId,
      String searchProfileId,
      LlmQueryPlanningProperties properties,
      String failureReason) {
    jdbc.update(
        """
        INSERT INTO query_plan_attempt(
            job_execution_id, search_profile_id, model_id, prompt_version, status, failure_reason)
        VALUES (:jobExecutionId, :searchProfileId, :modelId, :promptVersion, 'FAILED', :failureReason)
        """,
        Map.of(
            JOB_EXECUTION_ID,
            jobExecutionId,
            SEARCH_PROFILE_ID,
            searchProfileId,
            MODEL_ID,
            properties.model(),
            "promptVersion",
            properties.promptVersion(),
            "failureReason",
            failureReason));
  }

  public QueryPlanDecision findApplied(long jobExecutionId, String searchProfileId) {
    List<QueryPlanDecision> decisions =
        jdbc.query(
            """
            SELECT proposed_keywords, proposed_max_pages
            FROM query_plan_decision
            WHERE job_execution_id = :jobExecutionId AND search_profile_id = :searchProfileId
              AND applied = TRUE
            """,
            Map.of(JOB_EXECUTION_ID, jobExecutionId, SEARCH_PROFILE_ID, searchProfileId),
            QueryPlanningRepository::decision);
    return decisions.isEmpty() ? null : decisions.getFirst();
  }

  private static QueryPlanDecision decision(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new QueryPlanDecision(
        resultSet.getString("proposed_keywords"), resultSet.getInt("proposed_max_pages"));
  }

  public record LatestOutcome(String status, int recordsReceived) {}
}
