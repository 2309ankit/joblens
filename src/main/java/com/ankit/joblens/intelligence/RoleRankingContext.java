package com.ankit.joblens.intelligence;

import java.util.List;

public record RoleRankingContext(CandidateProfileConfig candidate, List<TargetRole> targetRoles) {

  public record TargetRole(
      Long id, String name, int priority, List<String> aliases, CalibrationPack calibrationPack) {}

  public record CalibrationPack(
      long id,
      String code,
      String version,
      String name,
      List<TitleSignal> titleSignals,
      List<SkillSignal> skillSignals) {}

  public record TitleSignal(String text, String level) {}

  public record SkillSignal(String name, String level) {}
}
