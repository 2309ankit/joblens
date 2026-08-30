package com.ankit.joblens.discovery;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SourceBoardRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public SourceBoardRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void register(
      SearchProfile discoveryProfile, GreenhouseBoardDetector.DetectedBoard detectedBoard) {
    if (discoveryProfile.workspaceId() == null || discoveryProfile.searchDefinitionId() == null) {
      return;
    }
    long sourceBoardId =
        jdbc.queryForObject(
            load("sql/discovery/register-source-board.sql"),
            Map.of(
                "sourceKey", detectedBoard.sourceKey(),
                "canonicalUrl", detectedBoard.canonicalUrl()),
            Long.class);
    var parameters =
        new MapSqlParameterSource()
            .addValue("workspaceId", discoveryProfile.workspaceId())
            .addValue("sourceBoardId", sourceBoardId)
            .addValue("searchDefinitionId", discoveryProfile.searchDefinitionId());
    jdbc.update(load("sql/discovery/link-workspace-source-board.sql"), parameters);
    jdbc.update(
        load("sql/discovery/upsert-discovered-greenhouse-profile.sql"),
        parameters
            .addValue(
                "profileId", profileId(discoveryProfile.workspaceId(), detectedBoard.sourceKey()))
            .addValue("sourceKey", detectedBoard.sourceKey())
            .addValue("keywords", discoveryProfile.keywords())
            .addValue("location", discoveryProfile.location())
            .addValue("employmentType", discoveryProfile.employmentType()));
  }

  public void markValidated(SearchProfile profile) {
    updateStatus(load("sql/discovery/validate-source-board.sql"), profile, null);
  }

  public void markFailed(SearchProfile profile, String reason) {
    updateStatus(load("sql/discovery/fail-source-board.sql"), profile, reason);
  }

  public List<SourceBoardView> list(UUID workspaceId) {
    return jdbc.query(
        load("sql/discovery/list-workspace-source-boards.sql"),
        Map.of("workspaceId", workspaceId),
        (resultSet, row) ->
            new SourceBoardView(
                resultSet.getString("source"),
                resultSet.getString("source_key"),
                resultSet.getString("canonical_url"),
                resultSet.getString("status"),
                resultSet.getString("failure_reason"),
                resultSet.getObject("first_discovered_at", java.time.OffsetDateTime.class),
                resultSet.getObject("last_discovered_at", java.time.OffsetDateTime.class),
                resultSet.getObject("validated_at", java.time.OffsetDateTime.class)));
  }

  private void updateStatus(String sql, SearchProfile profile, String reason) {
    if (!JobSource.GREENHOUSE.name().equals(profile.source())) {
      return;
    }
    var parameters =
        new MapSqlParameterSource()
            .addValue("source", profile.source())
            .addValue("sourceKey", profile.sourceKey());
    if (reason != null) {
      parameters.addValue("reason", reason);
    }
    jdbc.update(sql, parameters);
  }

  private static String profileId(UUID workspaceId, String sourceKey) {
    String workspaceToken = workspaceId.toString().replace("-", "").substring(0, 20);
    return "w-" + workspaceToken + "-gh-" + hash(sourceKey).substring(0, 12);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
