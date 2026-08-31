package com.ankit.joblens.intelligence;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CandidateProfileConfig(
    long id,
    String location,
    List<LocationPreference> locationPreferences,
    Set<String> roles,
    Set<String> domains,
    Map<Long, CandidateSkill> skills,
    Map<String, String> preferences) {
  public record LocationPreference(String countryCode, String location) {}

  public record CandidateSkill(String name, String status, double importance) {}
}
