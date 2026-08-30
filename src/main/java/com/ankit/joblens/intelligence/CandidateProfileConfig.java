package com.ankit.joblens.intelligence;

import java.util.Map;
import java.util.Set;

public record CandidateProfileConfig(
    long id,
    String location,
    Set<String> roles,
    Set<String> domains,
    Map<Long, CandidateSkill> skills,
    Map<String, String> preferences) {
  public record CandidateSkill(String name, String status, double importance) {}
}
