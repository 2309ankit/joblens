package com.ankit.joblens.intelligence;

import java.util.List;

public record JobScore(
    long normalizedJobId,
    long candidateProfileId,
    int total,
    int technical,
    int domain,
    int seniority,
    int location,
    int employment,
    int salary,
    int freshness,
    boolean qualifiesRecommended,
    List<Reason> reasons,
    RoleScore bestRole,
    List<RoleScore> roleScores) {
  public record Reason(String category, int points, String text) {}

  public record RoleScore(
      Long targetRoleId,
      String targetRoleName,
      int rolePriority,
      String policyVersion,
      String calibrationPackCode,
      String calibrationPackVersion,
      String calibrationPackName,
      int total,
      int title,
      int skill,
      int sector,
      int seniority,
      int location,
      int employment,
      int salary,
      int freshness,
      boolean qualifiesRecommended,
      List<Reason> reasons) {}
}
