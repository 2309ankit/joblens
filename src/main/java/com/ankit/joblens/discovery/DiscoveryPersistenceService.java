package com.ankit.joblens.discovery;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryPersistenceService {
  private final NamedParameterJdbcTemplate jdbc;
  private final List<SourceBoardDetector> sourceBoardDetectors;
  private final SourceBoardRepository sourceBoards;
  private final FailureReasonSanitizer failureReasons;

  public DiscoveryPersistenceService(
      NamedParameterJdbcTemplate jdbc,
      List<SourceBoardDetector> sourceBoardDetectors,
      SourceBoardRepository sourceBoards,
      FailureReasonSanitizer failureReasons) {
    this.jdbc = jdbc;
    this.sourceBoardDetectors = List.copyOf(sourceBoardDetectors);
    this.sourceBoards = sourceBoards;
    this.failureReasons = failureReasons;
  }

  public SearchProfile findNextActiveProfile(
      String lastCompletedProfileId, String requestedProfileId) {
    return findNextActiveProfile(lastCompletedProfileId, requestedProfileId, null);
  }

  public SearchProfile findNextActiveProfile(
      String lastCompletedProfileId, String requestedProfileId, UUID workspaceId) {
    List<SearchProfile> profiles =
        jdbc.query(
            load("sql/discovery/find-next-profile.sql"),
            new MapSqlParameterSource()
                .addValue("lastCompletedProfileId", lastCompletedProfileId)
                .addValue("requestedProfileId", requestedProfileId)
                .addValue("workspaceId", workspaceId),
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
                    resultSet.getObject("search_target_id", Long.class)));
    return profiles.isEmpty() ? null : profiles.getFirst();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public FetchRunState startOrResumeFetchRun(
      SearchProfile profile, long jobInstanceId, long jobExecutionId) {
    return jdbc.queryForObject(
        load("sql/discovery/start-fetch-run.sql"),
        new MapSqlParameterSource()
            .addValue("source", profile.source())
            .addValue("profileId", profile.profileId())
            .addValue("jobInstanceId", jobInstanceId)
            .addValue("jobExecutionId", jobExecutionId),
        (resultSet, rowNumber) ->
            new FetchRunState(
                resultSet.getLong("id"),
                resultSet.getInt("next_page"),
                resultSet.getString("status")));
  }

  public FetchRunState fetchRun(long fetchRunId) {
    return jdbc.queryForObject(
        load("sql/discovery/find-fetch-run.sql"),
        Map.of("fetchRunId", fetchRunId),
        (resultSet, rowNumber) ->
            new FetchRunState(
                resultSet.getLong("id"),
                resultSet.getInt("next_page"),
                resultSet.getString("status")));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistPage(
      long fetchRunId, SearchProfile profile, JobPage page, long jobExecutionId) {
    Integer expectedPage =
        jdbc.queryForObject(
            load("sql/discovery/lock-fetch-run.sql"),
            Map.of("fetchRunId", fetchRunId),
            Integer.class);
    if (expectedPage == null) {
      throw new IllegalStateException("Fetch run does not exist: " + fetchRunId);
    }
    if (page.page() < expectedPage) {
      return;
    }
    if (page.page() > expectedPage) {
      throw new IllegalStateException(
          "Fetch run "
              + fetchRunId
              + " expected page "
              + expectedPage
              + " but received "
              + page.page());
    }

    int newCount = 0;
    int changedCount = 0;
    int unchangedCount = 0;
    if (!page.jobs().isEmpty()) {
      Map<String, String> existingHashes = new HashMap<>();
      jdbc.query(
              load("sql/discovery/find-existing-page-jobs.sql"),
              new MapSqlParameterSource()
                  .addValue("source", profile.source())
                  .addValue(
                      "externalJobIds",
                      page.jobs().stream().map(RawSourceJob::externalJobId).toList()),
              (resultSet, rowNumber) ->
                  Map.entry(
                      resultSet.getString("external_job_id"), resultSet.getString("payload_hash")))
          .forEach(entry -> existingHashes.put(entry.getKey(), entry.getValue()));
      for (RawSourceJob job : page.jobs()) {
        String existingHash = existingHashes.get(job.externalJobId());
        if (existingHash == null) {
          newCount++;
        } else if (existingHash.equals(job.payloadHash())) {
          unchangedCount++;
        } else {
          changedCount++;
        }
      }
      var batch =
          page.jobs().stream()
              .map(
                  job ->
                      new MapSqlParameterSource()
                          .addValue("source", profile.source())
                          .addValue("externalJobId", job.externalJobId())
                          .addValue("profileId", profile.profileId())
                          .addValue("fetchRunId", fetchRunId)
                          .addValue("sourceUrl", job.sourceUrl())
                          .addValue("payloadHash", job.payloadHash())
                          .addValue("rawJson", job.rawJson())
                          .addValue("jobExecutionId", jobExecutionId))
              .toArray(MapSqlParameterSource[]::new);
      jdbc.batchUpdate(load("sql/discovery/upsert-raw-job.sql"), batch);
      if (profile.workspaceId() != null && profile.searchDefinitionId() != null) {
        jdbc.update(
            load("sql/discovery/upsert-workspace-sightings.sql"),
            new MapSqlParameterSource()
                .addValue("workspaceId", profile.workspaceId())
                .addValue("searchDefinitionId", profile.searchDefinitionId())
                .addValue("source", profile.source())
                .addValue(
                    "externalJobIds",
                    page.jobs().stream().map(RawSourceJob::externalJobId).toList()));
        page.jobs().stream()
            .map(RawSourceJob::sourceUrl)
            .flatMap(
                sourceUrl ->
                    sourceBoardDetectors.stream().map(detector -> detector.detect(sourceUrl)))
            .flatMap(java.util.Optional::stream)
            .filter(board -> !board.source().name().equals(profile.source()))
            .distinct()
            .forEach(board -> sourceBoards.register(profile, board));
      }
    }

    jdbc.update(
        load("sql/discovery/update-fetch-run-page.sql"),
        new MapSqlParameterSource()
            .addValue("recordCount", page.jobs().size())
            .addValue("newCount", newCount)
            .addValue("changedCount", changedCount)
            .addValue("unchangedCount", unchangedCount)
            .addValue("nextPage", page.page() + 1)
            .addValue("jobExecutionId", jobExecutionId)
            .addValue("fetchRunId", fetchRunId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeFetchRun(long fetchRunId, SearchProfile profile, long jobExecutionId) {
    jdbc.update(
        load("sql/discovery/complete-fetch-run.sql"),
        Map.of("jobExecutionId", jobExecutionId, "fetchRunId", fetchRunId));
    sourceBoards.markValidated(profile);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failFetchRun(
      long fetchRunId, SearchProfile profile, long jobExecutionId, Throwable failure) {
    String reason = failureReasons.sanitize(failure);
    jdbc.update(
        load("sql/discovery/fail-fetch-run.sql"),
        Map.of("reason", reason, "jobExecutionId", jobExecutionId, "fetchRunId", fetchRunId));
    sourceBoards.markFailed(profile, reason);
  }
}
