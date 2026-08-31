package com.ankit.joblens.onboarding;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.discovery.JoobleProperties;
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
  private final JoobleProperties joobleProperties;
  private final ProfileIntelligenceRepository profileIntelligenceRepository;

  public OnboardingRepository(
      NamedParameterJdbcTemplate jdbc,
      JoobleProperties joobleProperties,
      ProfileIntelligenceRepository profileIntelligenceRepository) {
    this.jdbc = jdbc;
    this.joobleProperties = joobleProperties;
    this.profileIntelligenceRepository = profileIntelligenceRepository;
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
    jdbc.update(
        load("sql/onboarding/copy-skill-suggestions.sql"),
        Map.of("sourceProfileVersionId", activeProfile.id(), "draftProfileVersionId", draftId));
    jdbc.update(
        load("sql/onboarding/copy-role-suggestions.sql"),
        Map.of("sourceProfileVersionId", activeProfile.id(), "draftProfileVersionId", draftId));
    jdbc.update(
        load("sql/onboarding/copy-term-suggestions.sql"),
        Map.of("sourceProfileVersionId", activeProfile.id(), "draftProfileVersionId", draftId));
    return latestProfile(workspaceId).orElseThrow();
  }

  public void replaceDraftSkills(UUID workspaceId, long profileVersionId, List<String> skills) {
    List<ProfileIntelligenceRepository.NamedValue> resolved =
        profileIntelligenceRepository.resolveSkills(workspaceId, skills);
    var parameters =
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("profileVersionId", profileVersionId)
            .addValue(
                "skillIds",
                resolved.stream()
                    .map(ProfileIntelligenceRepository.NamedValue::id)
                    .toArray(Long[]::new));
    jdbc.update(load("sql/onboarding/delete-draft-skills.sql"), parameters);
    jdbc.update(load("sql/onboarding/replace-draft-skills-by-id.sql"), parameters);
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
    List<String> targetRoles =
        profileIntelligenceRepository.resolveOrCreateRoles(
            workspaceId, csv(preferences.targetRoles()));
    jdbc.update(
        load("sql/onboarding/update-profile-preferences.sql"),
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId)
            .addValue("profileVersionId", profileVersionId)
            .addValue("targetRoles", targetRoles.toArray(String[]::new))
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
    long searchDefinitionId =
        jdbc.queryForObject(
            load("sql/onboarding/upsert-search-definition.sql"),
            new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("keywords", SearchKeywordNormalizer.normalize(preferences.keywords()))
                .addValue("maxPages", preferences.maxPages()),
            Long.class);
    jdbc.update(
        load("sql/onboarding/deactivate-search-targets.sql"),
        Map.of("searchDefinitionId", searchDefinitionId));
    int priority = 1;
    for (SearchTarget target : preferences.targets()) {
      jdbc.update(
          load("sql/onboarding/insert-search-target.sql"),
          new MapSqlParameterSource()
              .addValue("searchDefinitionId", searchDefinitionId)
              .addValue("countryCode", target.countryCode())
              .addValue("location", target.location())
              .addValue("priority", priority++));
    }
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
                    resultSet.getLong("id"),
                    resultSet.getString("keywords"),
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
    List<SearchTarget> targets =
        jdbc.query(
            load("sql/onboarding/list-search-targets.sql"),
            Map.of("searchDefinitionId", definition.id()),
            (resultSet, row) ->
                new SearchTarget(
                    resultSet.getString("country_code"), resultSet.getString("location")));
    OnboardingProfile current = profile.get();
    return Optional.of(
        new SearchPreferences(
            String.join(", ", current.targetRoles()),
            String.join(", ", current.targetDomains()),
            current.primaryLocation(),
            definition.keywords(),
            SearchTarget.format(targets),
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
    jdbc.update(
        load("sql/onboarding/deactivate-workspace-search-profiles.sql"),
        Map.of("workspaceId", workspaceId));
    SearchDefinition definition = searchDefinition(workspaceId);
    for (StoredSearchTarget target : storedTargets(definition.id())) {
      upsertSearchProfile(
          workspaceId,
          profileId(workspaceId, "adzuna", target),
          "ADZUNA",
          target.countryCode().toLowerCase(),
          definition,
          target,
          preferences);
      if (joobleProperties.hasCredentials()
          && joobleProperties.supportsCountry(target.countryCode())) {
        upsertSearchProfile(
            workspaceId,
            profileId(workspaceId, "jooble", target),
            "JOOBLE",
            target.countryCode().toLowerCase(),
            definition,
            target,
            preferences);
      }
    }
  }

  private void upsertSearchProfile(
      UUID workspaceId,
      String profileId,
      String source,
      String sourceKey,
      SearchDefinition definition,
      StoredSearchTarget target,
      SearchPreferences preferences) {
    jdbc.update(
        load("sql/onboarding/upsert-workspace-search-profile.sql"),
        new MapSqlParameterSource()
            .addValue("profileId", profileId)
            .addValue("source", source)
            .addValue("sourceKey", sourceKey)
            .addValue("keywords", definition.keywords())
            .addValue("location", target.location())
            .addValue("employmentType", employmentType(preferences.employmentPreference()))
            .addValue("workspaceId", workspaceId)
            .addValue("searchDefinitionId", definition.id())
            .addValue("searchTargetId", target.id())
            .addValue("maxPages", preferences.maxPages()));
  }

  private SearchDefinition searchDefinition(UUID workspaceId) {
    return jdbc.queryForObject(
        load("sql/onboarding/find-search-definition.sql"),
        Map.of("workspaceId", workspaceId),
        (resultSet, row) ->
            new SearchDefinition(
                resultSet.getLong("id"),
                resultSet.getString("keywords"),
                resultSet.getInt("max_pages")));
  }

  private List<StoredSearchTarget> storedTargets(long searchDefinitionId) {
    return jdbc.query(
        load("sql/onboarding/list-search-targets.sql"),
        Map.of("searchDefinitionId", searchDefinitionId),
        (resultSet, row) ->
            new StoredSearchTarget(
                resultSet.getLong("id"),
                resultSet.getString("country_code"),
                resultSet.getString("location")));
  }

  private static String profileId(UUID workspaceId, String source, StoredSearchTarget target) {
    return "w-"
        + workspaceToken(workspaceId)
        + "-"
        + source
        + "-"
        + hash(target.countryCode() + "|" + target.location()).substring(0, 10);
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

  private static String employmentType(String value) {
    String normalized = value == null ? "ANY" : value.trim().toUpperCase();
    return normalized.equals("PERMANENT") || normalized.equals("CONTRACT") ? normalized : "ANY";
  }

  private static String workspaceToken(UUID workspaceId) {
    return workspaceId.toString().replace("-", "").substring(0, 20);
  }

  private static List<String> strings(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    return List.of((String[]) array.getArray());
  }

  private record SearchDefinition(long id, String keywords, int maxPages) {}

  private record StoredSearchTarget(long id, String countryCode, String location) {}
}
