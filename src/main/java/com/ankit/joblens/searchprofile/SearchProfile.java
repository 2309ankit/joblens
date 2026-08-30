package com.ankit.joblens.searchprofile;

import java.util.UUID;

public record SearchProfile(
    String profileId,
    String source,
    String sourceKey,
    String keywords,
    String location,
    String includeSkills,
    String excludeSkills,
    String employmentType,
    boolean active,
    UUID workspaceId,
    Long searchDefinitionId,
    Integer maxPages) {

  public SearchProfile(
      String profileId,
      String source,
      String sourceKey,
      String keywords,
      String location,
      String includeSkills,
      String excludeSkills,
      String employmentType,
      boolean active) {
    this(
        profileId,
        source,
        sourceKey,
        keywords,
        location,
        includeSkills,
        excludeSkills,
        employmentType,
        active,
        null,
        null,
        null);
  }
}
