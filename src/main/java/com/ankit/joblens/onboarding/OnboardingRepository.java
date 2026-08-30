package com.ankit.joblens.onboarding;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.sql.Array;
import java.sql.SQLException;
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

  public List<String> skillCatalog() {
    return jdbc.query(
        load("sql/profile/catalog.sql"), Map.of(), (resultSet, row) -> resultSet.getString(1));
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
    jdbc.update(
        load("sql/onboarding/upsert-search-definition.sql"),
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("keywords", preferences.keywords())
            .addValue("location", preferences.searchLocation())
            .addValue("countryCode", preferences.countryCode().toLowerCase())
            .addValue(
                "enabledSources", safeSources(preferences.enabledSources()).toArray(String[]::new))
            .addValue("maxPages", preferences.maxPages()));
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
    return candidateId;
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

  private static List<String> strings(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    return List.of((String[]) array.getArray());
  }
}
