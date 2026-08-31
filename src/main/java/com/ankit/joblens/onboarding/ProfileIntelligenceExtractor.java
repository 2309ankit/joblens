package com.ankit.joblens.onboarding;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProfileIntelligenceExtractor {
  private static final Pattern EXPERIENCE_HEADING =
      Pattern.compile("(?i)^(professional |work |employment )?experience:?$");
  private static final Pattern SECTION_HEADING =
      Pattern.compile(
          "(?i)^(education|skills|technical skills|projects|certifications|achievements|professional summary|summary|profile|objective):?$");

  public Extraction extract(String text, List<SkillDefinition> skills, List<RoleDefinition> roles) {
    List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    var detectedSkills = new ArrayList<DetectedSkill>();
    for (SkillDefinition skill : skills) {
      Match match = firstMatch(lines, skill.terms());
      if (match != null) {
        BigDecimal confidence =
            match.term().equalsIgnoreCase(skill.name())
                ? new BigDecimal("0.950")
                : new BigDecimal("0.900");
        detectedSkills.add(
            new DetectedSkill(
                skill.id(),
                skill.name(),
                skill.category(),
                match.term(),
                match.line(),
                confidence));
      }
    }
    detectedSkills.sort(Comparator.comparing(DetectedSkill::name));

    int experienceLine = experienceLine(lines);
    var detectedRoles = new ArrayList<DetectedRole>();
    for (RoleDefinition role : roles) {
      RoleMatch match = bestRoleMatch(lines, experienceLine, role.terms());
      if (match != null) {
        detectedRoles.add(
            new DetectedRole(
                role.id(),
                role.name(),
                role.category(),
                match.source(),
                match.evidence(),
                match.confidence(),
                0));
      }
    }
    detectedRoles.sort(
        Comparator.comparing(DetectedRole::confidence)
            .reversed()
            .thenComparing(DetectedRole::name));
    var rankedRoles = new ArrayList<DetectedRole>();
    for (int index = 0; index < detectedRoles.size(); index++) {
      DetectedRole role = detectedRoles.get(index);
      rankedRoles.add(
          new DetectedRole(
              role.id(),
              role.name(),
              role.category(),
              role.evidenceSource(),
              role.evidence(),
              role.confidence(),
              index + 1));
    }
    return new Extraction(List.copyOf(detectedSkills), List.copyOf(rankedRoles));
  }

  private static RoleMatch bestRoleMatch(
      List<String> lines, int experienceLine, List<String> terms) {
    RoleMatch best = null;
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      for (String term : terms) {
        if (!containsTerm(line, term)) {
          continue;
        }
        RoleMatch candidate;
        if (index <= 7 && (experienceLine < 0 || index < experienceLine)) {
          candidate = new RoleMatch("RESUME_HEADLINE", line, new BigDecimal("0.950"), index);
        } else if (experienceLine >= 0
            && index > experienceLine
            && index <= experienceLine + 12
            && !SECTION_HEADING.matcher(line).matches()) {
          candidate = new RoleMatch("RECENT_EXPERIENCE", line, new BigDecimal("0.850"), index);
        } else {
          candidate = new RoleMatch("RESUME_BODY", line, new BigDecimal("0.600"), index);
        }
        if (best == null
            || candidate.confidence().compareTo(best.confidence()) > 0
            || (candidate.confidence().equals(best.confidence())
                && candidate.lineIndex() < best.lineIndex())) {
          best = candidate;
        }
      }
    }
    return best;
  }

  private static Match firstMatch(List<String> lines, List<String> terms) {
    return terms.stream()
        .sorted(Comparator.comparingInt(String::length).reversed())
        .map(term -> firstMatch(lines, term))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static Match firstMatch(List<String> lines, String term) {
    for (String line : lines) {
      if (containsTerm(line, term)) {
        return new Match(term, evidence(line));
      }
    }
    return null;
  }

  private static boolean containsTerm(String text, String term) {
    return Pattern.compile(
            "(?<![A-Za-z0-9+#])" + Pattern.quote(term) + "(?![A-Za-z0-9+#])",
            Pattern.CASE_INSENSITIVE)
        .matcher(text)
        .find();
  }

  private static int experienceLine(List<String> lines) {
    for (int index = 0; index < lines.size(); index++) {
      if (EXPERIENCE_HEADING.matcher(lines.get(index)).matches()) {
        return index;
      }
    }
    return -1;
  }

  private static String evidence(String line) {
    return line.length() <= 300 ? line : line.substring(0, 297) + "...";
  }

  public record SkillDefinition(long id, String name, String category, List<String> terms) {}

  public record RoleDefinition(long id, String name, String category, List<String> terms) {}

  public record DetectedSkill(
      long id,
      String name,
      String category,
      String matchedTerm,
      String evidence,
      BigDecimal confidence) {}

  public record DetectedRole(
      long id,
      String name,
      String category,
      String evidenceSource,
      String evidence,
      BigDecimal confidence,
      int priority) {}

  public record Extraction(List<DetectedSkill> skills, List<DetectedRole> roles) {}

  private record Match(String term, String line) {}

  private record RoleMatch(String source, String evidence, BigDecimal confidence, int lineIndex) {
    private RoleMatch {
      evidence = ProfileIntelligenceExtractor.evidence(evidence);
    }
  }
}
