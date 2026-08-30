package com.ankit.joblens.onboarding;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OnboardingRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public OnboardingRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long saveResume(
      UUID workspaceId, String filename, String contentType, long sizeBytes, String hash) {
    return jdbc.queryForObject(
        load("sql/onboarding/insert-resume.sql"),
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("filename", filename)
            .addValue("contentType", contentType)
            .addValue("sizeBytes", sizeBytes)
            .addValue("contentHash", hash),
        Long.class);
  }

  public long createDraft(UUID workspaceId, long resumeId, String summary) {
    return jdbc.queryForObject(
        load("sql/onboarding/create-draft.sql"),
        Map.of("workspaceId", workspaceId, "resumeId", resumeId, "summary", summary),
        Long.class);
  }

  public void addSkills(long profileVersionId, List<String> skills) {
    for (String skill : skills) {
      jdbc.update(
          load("sql/onboarding/add-draft-skill.sql"),
          Map.of("profileVersionId", profileVersionId, "skill", skill));
    }
  }

  public OnboardingProfile forkDraft(UUID workspaceId, OnboardingProfile activeProfile) {
    long draftId =
        jdbc.queryForObject(
            load("sql/onboarding/create-draft-from-active.sql"),
            Map.of("workspaceId", workspaceId, "profileVersionId", activeProfile.id()),
            Long.class);
    jdbc.update(
        load("sql/onboarding/copy-draft-skills.sql"),
        Map.of("sourceProfileVersionId", activeProfile.id(), "draftProfileVersionId", draftId));
    return latestProfile(workspaceId).orElseThrow();
  }

  public List<String> skillCatalog() {
    return jdbc.query(
        load("sql/profile/catalog.sql"), Map.of(), (resultSet, row) -> resultSet.getString(1));
  }

  public void replaceDraftSkills(UUID workspaceId, long profileVersionId, List<String> skills) {
    var parameters =
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("profileVersionId", profileVersionId)
            .addValue("skills", skills);
    jdbc.update(load("sql/onboarding/delete-draft-skills.sql"), parameters);
    jdbc.update(load("sql/onboarding/insert-draft-skills.sql"), parameters);
  }

  public Optional<OnboardingProfile> latestProfile(UUID workspaceId) {
    var profiles =
        jdbc.query(
            load("sql/onboarding/find-latest-profile.sql"),
            Map.of("workspaceId", workspaceId),
            (resultSet, row) ->
                new OnboardingProfile(
                    resultSet.getLong("id"),
                    resultSet.getInt("version"),
                    resultSet.getString("status"),
                    resultSet.getString("summary"),
                    strings(resultSet.getArray("target_roles")),
                    strings(resultSet.getArray("target_domains")),
                    resultSet.getString("primary_location"),
                    List.of()));
    if (profiles.isEmpty()) {
      return Optional.empty();
    }
    OnboardingProfile profile = profiles.getFirst();
    List<String> skills =
        jdbc.query(
            load("sql/onboarding/find-profile-skills.sql"),
            Map.of("profileVersionId", profile.id()),
            (resultSet, row) -> resultSet.getString(1));
    return Optional.of(
        new OnboardingProfile(
            profile.id(),
            profile.version(),
            profile.status(),
            profile.summary(),
            profile.targetRoles(),
            profile.targetDomains(),
            profile.primaryLocation(),
            skills));
  }

  public void savePreferences(
      UUID workspaceId, long profileVersionId, SearchPreferences preferences) {
    jdbc.update(
        load("sql/onboarding/update-profile-preferences.sql"),
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("profileVersionId", profileVersionId)
            .addValue("targetRoles", csv(preferences.targetRoles()).toArray(String[]::new))
            .addValue("targetDomains", csv(preferences.targetDomains()).toArray(String[]::new))
            .addValue("primaryLocation", preferences.primaryLocation()));
    Map<String, String> values = new LinkedHashMap<>();
    values.put("weight.technical", "40");
    values.put("weight.domain", "15");
    values.put("weight.seniority", "10");
    values.put("weight.location", "10");
    values.put("weight.employment", "10");
    values.put("weight.salary", "10");
    values.put("weight.freshness", "5");
    values.put("salary.missing.points", "3");
    values.put("freshness.days.full", "7");
    values.put("freshness.days.half", "30");
    values.put("employment.preference", preferences.employmentPreference());
    values.put("work.preference", preferences.workPreference());
    values.forEach(
        (key, value) ->
            jdbc.update(
                load("sql/onboarding/upsert-preference.sql"),
                Map.of("workspaceId", workspaceId, "key", key, "value", value)));
    jdbc.queryForObject(
        load("sql/onboarding/upsert-search-definition.sql"),
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("keywords", preferences.keywords())
            .addValue("location", preferences.searchLocation())
            .addValue("countryCode", preferences.countryCode().toLowerCase())
            .addValue(
                "enabledSources", safeSources(preferences.enabledSources()).toArray(String[]::new))
            .addValue(
                "greenhouseBoards", boards(preferences.greenhouseBoards()).toArray(String[]::new))
            .addValue("maxPages", preferences.maxPages()),
        Long.class);
  }

  public Optional<SearchPreferences> preferences(UUID workspaceId) {
    Optional<OnboardingProfile> profile = latestProfile(workspaceId);
    if (profile.isEmpty()) {
      return Optional.empty();
    }
    var definitions =
        jdbc.query(
            load("sql/onboarding/find-search-definition.sql"),
            Map.of("workspaceId", workspaceId),
            (resultSet, row) ->
                new SearchDefinition(
                    resultSet.getString("keywords"),
                    resultSet.getString("location"),
                    resultSet.getString("country_code"),
                    strings(resultSet.getArray("enabled_sources")),
                    strings(resultSet.getArray("greenhouse_boards")),
                    resultSet.getInt("max_pages")));
    if (definitions.isEmpty()) {
      return Optional.empty();
    }
    Map<String, String> values = new LinkedHashMap<>();
    jdbc.query(
            load("sql/onboarding/list-preferences.sql"),
            Map.of("workspaceId", workspaceId),
            (resultSet, row) ->
                Map.entry(
                    resultSet.getString("preference_key"), resultSet.getString("preference_value")))
        .forEach(entry -> values.put(entry.getKey(), entry.getValue()));
    SearchDefinition definition = definitions.getFirst();
    OnboardingProfile current = profile.get();
    return Optional.of(
        new SearchPreferences(
            String.join(", ", current.targetRoles()),
            String.join(", ", current.targetDomains()),
            current.primaryLocation(),
            definition.keywords(),
            definition.location(),
            definition.countryCode(),
            definition.enabledSources(),
            String.join(", ", definition.greenhouseBoards()),
            definition.maxPages(),
            values.getOrDefault("employment.preference", "ANY"),
            values.getOrDefault("work.preference", "REMOTE,HYBRID,ONSITE")));
  }

  public long confirm(UUID workspaceId, OnboardingProfile profile) {
    long candidateId =
        findCandidate(workspaceId)
            .orElseGet(
                () ->
                    jdbc.queryForObject(
                        load("sql/onboarding/create-candidate.sql"),
                        profileParameters(workspaceId, profile),
                        Long.class));
    var parameters =
        profileParameters(workspaceId, profile)
            .addValue("candidateProfileId", candidateId)
            .addValue("profileVersionId", profile.id());
    jdbc.update(load("sql/onboarding/update-candidate.sql"), parameters);
    jdbc.update(load("sql/onboarding/replace-candidate-skills.sql"), parameters);
    jdbc.update(load("sql/onboarding/copy-candidate-skills.sql"), parameters);
    jdbc.update(load("sql/onboarding/replace-candidate-preferences.sql"), parameters);
    jdbc.update(load("sql/onboarding/copy-candidate-preferences.sql"), parameters);
    jdbc.update(load("sql/onboarding/activate-profile.sql"), parameters);
    int activated = jdbc.update(load("sql/onboarding/activate-selected-profile.sql"), parameters);
    if (activated != 1) {
      throw new IllegalStateException("Only a draft profile can be confirmed");
    }
    jdbc.update(load("sql/onboarding/link-candidate.sql"), parameters);
    preferences(workspaceId).ifPresent(value -> syncSearchProfiles(workspaceId, value));
    return candidateId;
  }

  private void syncSearchProfiles(UUID workspaceId, SearchPreferences preferences) {
    List<String> sources = safeSources(preferences.enabledSources());
    jdbc.update(
        load("sql/onboarding/deactivate-workspace-search-profiles.sql"),
        Map.of("workspaceId", workspaceId));
    if (sources.contains("ADZUNA")) {
      upsertSearchProfile(
          workspaceId,
          "w-" + workspaceToken(workspaceId) + "-adzuna",
          "ADZUNA",
          preferences.countryCode().toLowerCase(),
          preferences);
    }
    if (sources.contains("GREENHOUSE")) {
      for (String board : boards(preferences.greenhouseBoards())) {
        upsertSearchProfile(
            workspaceId,
            "w-" + workspaceToken(workspaceId) + "-gh-" + token(board),
            "GREENHOUSE",
            board,
            preferences);
      }
    }
  }

  private void upsertSearchProfile(
      UUID workspaceId,
      String profileId,
      String source,
      String sourceKey,
      SearchPreferences preferences) {
    long searchDefinitionId =
        jdbc.queryForObject(
            load("sql/onboarding/find-search-definition.sql"),
            Map.of("workspaceId", workspaceId),
            (resultSet, row) -> resultSet.getLong("id"));
    jdbc.update(
        load("sql/onboarding/upsert-workspace-search-profile.sql"),
        new MapSqlParameterSource()
            .addValue("profileId", profileId)
            .addValue("source", source)
            .addValue("sourceKey", sourceKey)
            .addValue("keywords", preferences.keywords())
            .addValue("location", preferences.searchLocation())
            .addValue("employmentType", employmentType(preferences.employmentPreference()))
            .addValue("workspaceId", workspaceId)
            .addValue("searchDefinitionId", searchDefinitionId)
            .addValue("maxPages", preferences.maxPages()));
  }

  private Optional<Long> findCandidate(UUID workspaceId) {
    return jdbc
        .queryForList(
            load("sql/onboarding/find-workspace-candidate.sql"),
            Map.of("workspaceId", workspaceId),
            Long.class)
        .stream()
        .findFirst();
  }

  private static MapSqlParameterSource profileParameters(
      UUID workspaceId, OnboardingProfile profile) {
    return new MapSqlParameterSource()
        .addValue("workspaceId", workspaceId)
        .addValue("name", "workspace-" + workspaceId)
        .addValue("summary", profile.summary())
        .addValue("targetRoles", profile.targetRoles().toArray(String[]::new))
        .addValue("targetDomains", profile.targetDomains().toArray(String[]::new))
        .addValue("primaryLocation", profile.primaryLocation());
  }

  private static List<String> csv(String value) {
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .distinct()
        .toList();
  }

  private static List<String> safeSources(List<String> sources) {
    if (sources == null || sources.isEmpty()) {
      return List.of("ADZUNA");
    }
    return sources.stream()
        .map(String::toUpperCase)
        .filter(source -> source.equals("ADZUNA") || source.equals("GREENHOUSE"))
        .distinct()
        .toList();
  }

  private static List<String> boards(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .map(String::toLowerCase)
        .filter(board -> board.matches("[a-z0-9_-]+"))
        .distinct()
        .toList();
  }

  private static String employmentType(String value) {
    String normalized = value == null ? "ANY" : value.trim().toUpperCase();
    return normalized.equals("PERMANENT") || normalized.equals("CONTRACT") ? normalized : "ANY";
  }

  private static String workspaceToken(UUID workspaceId) {
    return workspaceId.toString().replace("-", "").substring(0, 20);
  }

  private static String token(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 12);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static List<String> strings(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    return List.of((String[]) array.getArray());
  }

  private record SearchDefinition(
      String keywords,
      String location,
      String countryCode,
      List<String> enabledSources,
      List<String> greenhouseBoards,
      int maxPages) {}
}
