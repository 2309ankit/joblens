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
    List<Reason> reasons) {
  public record Reason(String category, int points, String text) {}
}
