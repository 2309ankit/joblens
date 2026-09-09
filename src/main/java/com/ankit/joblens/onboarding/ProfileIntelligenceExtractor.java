package com.ankit.joblens.onboarding;

import com.ankit.joblens.intelligence.PhraseAutomaton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProfileIntelligenceExtractor {
  public static final String EXTRACTOR_VERSION = "esco-deterministic-v4";

  private static final Pattern EXPLICIT_GROUP_LABEL =
      Pattern.compile("\\b([A-Z][A-Za-z/&-]*(?: [A-Z&][A-Za-z/&-]*){1,5}):");
  private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z][A-Z0-9+.-]{2,}\\b");
  private static final Pattern CONTACT_NOISE =
      Pattern.compile(
          "(?i)(https?://|www\\.|\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b|(?:blog|portfolio|website|linkedin|github|medium|email|phone|mobile)\\s*:|\\.com(?:/|\\b))");
  private static final Set<String> CANDIDATE_STOP_WORDS =
      Set.of("and", "or", "with", "skills", "competencies", "tools", "ecosystems");

  private final ResumeDocumentParser documentParser = new ResumeDocumentParser();

  public Extraction extract(String text, List<SkillDefinition> skills, List<RoleDefinition> roles) {
    ResumeDocument document = documentParser.parse(text);
    List<TermHit<SkillDefinition>> skillHits = findTerms(document.text(), skills);
    List<TermHit<RoleDefinition>> roleHits = findTerms(document.text(), roles);

    var rejected = new ArrayList<TermSuggestion>();
    var detectedSkills = new ArrayList<DetectedSkill>();
    for (SkillDefinition skill : skills) {
      List<TermHit<SkillDefinition>> hits =
          skillHits.stream().filter(hit -> hit.definition().id() == skill.id()).toList();
      TermHit<SkillDefinition> best = bestSkillHit(document, hits, rejected);
      if (best == null) {
        continue;
      }
      ResumeDocument.Section section = document.sectionAt(best.start());
      detectedSkills.add(
          new DetectedSkill(
              skill.id(),
              skill.name(),
              skill.category(),
              best.surface(),
              document.evidenceAt(best.start()),
              skillStrength(section, best.canonical()),
              section.name(),
              best.canonical() ? "EXACT_CANONICAL" : "EXACT_ALIAS",
              best.start(),
              best.end(),
              skill.taxonomyVersion(),
              section.explicitlyListsSkills()));
    }
    detectedSkills.sort(Comparator.comparing(DetectedSkill::name));

    var detectedRoles = new ArrayList<DetectedRole>();
    for (RoleDefinition role : roles) {
      TermHit<RoleDefinition> best =
          roleHits.stream()
              .filter(hit -> hit.definition().id() == role.id())
              .filter(hit -> roleContext(document, hit))
              .max(roleComparator(document))
              .orElse(null);
      if (best == null || !safeShortTerm(best, document.sectionAt(best.start()))) {
        continue;
      }
      ResumeDocument.Section section = document.sectionAt(best.start());
      String source = roleSource(document, section, best.start());
      detectedRoles.add(
          new DetectedRole(
              role.id(),
              role.name(),
              role.category(),
              source,
              document.evidenceAt(best.start()),
              roleStrength(source),
              0,
              best.canonical() ? "EXACT_CANONICAL" : "EXACT_ALIAS",
              best.start(),
              best.end(),
              role.taxonomyVersion()));
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
              index + 1,
              role.matchType(),
              role.startOffset(),
              role.endOffset(),
              role.taxonomyVersion()));
    }

    var allTerms = new ArrayList<TermSuggestion>();
    allTerms.addAll(explicitCandidates(document, detectedSkills, rankedRoles, rejected));
    allTerms.addAll(rejected);
    return new Extraction(
        List.copyOf(detectedSkills), List.copyOf(rankedRoles), List.copyOf(rankTerms(allTerms)));
  }

  private static TermHit<SkillDefinition> bestSkillHit(
      ResumeDocument document, List<TermHit<SkillDefinition>> hits, List<TermSuggestion> rejected) {
    return hits.stream()
        .filter(
            hit -> {
              ResumeDocument.Section section = document.sectionAt(hit.start());
              if (section == ResumeDocument.Section.CONTACT
                  && CONTACT_NOISE.matcher(document.evidenceAt(hit.start())).find()) {
                return false;
              }
              boolean safe = safeShortTerm(hit, section);
              if (!safe) {
                if (section.explicitlyListsSkills()) {
                  rejected.add(
                      new TermSuggestion(
                          "SKILL",
                          hit.surface(),
                          section.name(),
                          document.evidenceAt(hit.start()),
                          new BigDecimal("0.100"),
                          "REJECTED",
                          0));
                }
              }
              return safe;
            })
        .max(skillComparator(document))
        .orElse(null);
  }

  private static <D extends TermDefinition> boolean safeShortTerm(
      TermHit<D> hit, ResumeDocument.Section section) {
    String term = hit.term().trim();
    if (term.length() == 1) {
      return false;
    }
    if (term.length() == 2) {
      return section.explicitlyListsSkills()
          && hit.surface().equals(hit.surface().toUpperCase(Locale.ROOT));
    }
    if (term.length() <= 4 && hit.surface().equals(hit.surface().toLowerCase(Locale.ROOT))) {
      return false;
    }
    return true;
  }

  private static Comparator<TermHit<SkillDefinition>> skillComparator(ResumeDocument document) {
    return Comparator.<TermHit<SkillDefinition>>comparingInt(hit -> hit.term().length())
        .thenComparing(hit -> skillStrength(document.sectionAt(hit.start()), hit.canonical()))
        .thenComparing(Comparator.comparingInt(TermHit<SkillDefinition>::start).reversed());
  }

  private static Comparator<TermHit<RoleDefinition>> roleComparator(ResumeDocument document) {
    return Comparator.<TermHit<RoleDefinition>, BigDecimal>comparing(
            hit -> roleStrength(roleSource(document, document.sectionAt(hit.start()), hit.start())))
        .thenComparingInt(hit -> hit.term().length())
        .thenComparing(Comparator.comparingInt(TermHit<RoleDefinition>::start).reversed());
  }

  private static BigDecimal skillStrength(ResumeDocument.Section section, boolean canonicalMatch) {
    BigDecimal base =
        switch (section) {
          case CORE_COMPETENCIES, SKILLS, TOOLS -> new BigDecimal("0.990");
          case EXPERIENCE -> new BigDecimal("0.900");
          case SUMMARY -> new BigDecimal("0.850");
          case DOMAIN_EXPERTISE -> new BigDecimal("0.800");
          case EDUCATION -> new BigDecimal("0.600");
          default -> new BigDecimal("0.700");
        };
    return canonicalMatch ? base : base.subtract(new BigDecimal("0.030"));
  }

  private static String roleSource(
      ResumeDocument document, ResumeDocument.Section section, int start) {
    if (section == ResumeDocument.Section.EXPERIENCE) {
      return "RECENT_EXPERIENCE";
    }
    if (section == ResumeDocument.Section.SUMMARY) {
      return "PROFESSIONAL_SUMMARY";
    }
    if (section == ResumeDocument.Section.CONTACT && document.beforeExperience(start)) {
      return "RESUME_HEADLINE";
    }
    return "RESUME_BODY";
  }

  private static BigDecimal roleStrength(String source) {
    return switch (source) {
      case "RESUME_HEADLINE" -> new BigDecimal("0.950");
      case "RECENT_EXPERIENCE" -> new BigDecimal("0.900");
      case "PROFESSIONAL_SUMMARY" -> new BigDecimal("0.800");
      default -> new BigDecimal("0.600");
    };
  }

  private static boolean roleContext(ResumeDocument document, TermHit<RoleDefinition> hit) {
    ResumeDocument.Section section = document.sectionAt(hit.start());
    if (CONTACT_NOISE.matcher(document.evidenceAt(hit.start())).find()) {
      return false;
    }
    if (section == ResumeDocument.Section.SUMMARY) {
      return document.beforeExperience(hit.start());
    }
    if (section == ResumeDocument.Section.CONTACT) {
      String evidence = document.evidenceAt(hit.start());
      return document.beforeExperience(hit.start())
          && evidence.length() <= 100
          && evidence.split("\\s+").length <= 8;
    }
    if (section != ResumeDocument.Section.EXPERIENCE) {
      return false;
    }
    String evidence = document.evidenceAt(hit.start());
    return evidence.matches(
        ".*(?i)(\\b(19|20)\\d{2}\\b|present|current|\\||—|-\\s*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)).*");
  }

  private static List<TermSuggestion> explicitCandidates(
      ResumeDocument document,
      List<DetectedSkill> skills,
      List<DetectedRole> roles,
      List<TermSuggestion> rejected) {
    Set<String> known = new LinkedHashSet<>();
    skills.forEach(
        skill -> {
          known.add(key(skill.name()));
          known.add(key(skill.matchedTerm()));
        });
    roles.forEach(role -> known.add(key(role.name())));
    rejected.forEach(term -> known.add(key(term.normalizedTerm())));
    var candidates = new LinkedHashMap<String, TermSuggestion>();
    for (ResumeDocument.Segment segment : document.segments()) {
      if (!segment.section().explicitlyListsSkills()) {
        continue;
      }
      String grouped = EXPLICIT_GROUP_LABEL.matcher(segment.text()).replaceAll("$1;");
      for (String rawCandidate : grouped.split("[,;]|\\s+&\\s+")) {
        addCandidate(candidates, known, segment, rawCandidate);
        Matcher acronyms = ACRONYM.matcher(rawCandidate);
        while (acronyms.find()) {
          addCandidate(candidates, known, segment, acronyms.group());
        }
        int open = rawCandidate.indexOf('(');
        int close = rawCandidate.indexOf(')', open + 1);
        if (open >= 0 && close > open) {
          addCandidate(candidates, known, segment, rawCandidate.substring(0, open));
          for (String nested : rawCandidate.substring(open + 1, close).split("[/,]")) {
            addCandidate(candidates, known, segment, nested);
          }
        }
      }
    }
    return List.copyOf(candidates.values());
  }

  private static void addCandidate(
      Map<String, TermSuggestion> candidates,
      Set<String> known,
      ResumeDocument.Segment segment,
      String rawCandidate) {
    String candidate =
        rawCandidate
            .replaceAll("^[^\\p{L}\\p{N}+#]+|[^\\p{L}\\p{N}+#.)-]+$", "")
            .replaceAll("[.!?,;:]+$", "")
            .replaceAll("\\s+", " ")
            .trim();
    String normalized = key(candidate);
    if (candidate.length() < 2
        || candidate.length() > 100
        || candidate.split("\\s+").length > 10
        || CANDIDATE_STOP_WORDS.contains(normalized)
        || known.contains(normalized)
        || candidate.matches(".*[.!?].*[.!?].*")) {
      return;
    }
    String evidence =
        segment.text().length() <= 300 ? segment.text() : segment.text().substring(0, 297) + "...";
    candidates.putIfAbsent(
        normalized,
        new TermSuggestion(
            "SKILL",
            candidate,
            segment.section().name(),
            evidence,
            new BigDecimal("0.750"),
            "SUGGESTED",
            0));
  }

  private static List<TermSuggestion> rankTerms(List<TermSuggestion> terms) {
    Map<String, TermSuggestion> distinct = new LinkedHashMap<>();
    terms.stream()
        .sorted(
            Comparator.comparing(TermSuggestion::evidenceStrength)
                .reversed()
                .thenComparing(TermSuggestion::normalizedTerm))
        .forEach(
            term ->
                distinct.putIfAbsent(term.reviewState() + ":" + key(term.normalizedTerm()), term));
    var ranked = new ArrayList<TermSuggestion>();
    int priority = 1;
    for (TermSuggestion term : distinct.values()) {
      ranked.add(
          new TermSuggestion(
              term.termKind(),
              term.normalizedTerm(),
              term.evidenceSection(),
              term.evidence(),
              term.evidenceStrength(),
              term.reviewState(),
              priority++));
    }
    return ranked;
  }

  private static String key(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }

  private static <D extends TermDefinition> List<TermHit<D>> findTerms(
      String text, List<D> definitions) {
    List<PhraseAutomaton.Entry<IndexedTerm<D>>> entries =
        definitions.stream()
            .flatMap(
                definition ->
                    definition.terms().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(term -> !term.isBlank())
                        .distinct()
                        .map(
                            term ->
                                new PhraseAutomaton.Entry<>(
                                    new IndexedTerm<>(
                                        definition, term, term.equalsIgnoreCase(definition.name())),
                                    term)))
            .toList();
    return new PhraseAutomaton<>(entries)
        .find(text).stream()
            .map(
                hit ->
                    new TermHit<>(
                        hit.value().definition(),
                        hit.value().term(),
                        hit.surface(),
                        hit.start(),
                        hit.end(),
                        hit.value().canonical()))
            .toList();
  }

  public sealed interface TermDefinition permits SkillDefinition, RoleDefinition {
    long id();

    String name();

    String category();

    List<String> terms();

    String taxonomyVersion();
  }

  public record SkillDefinition(
      long id, String name, String category, List<String> terms, String taxonomyVersion)
      implements TermDefinition {
    public SkillDefinition(long id, String name, String category, List<String> terms) {
      this(id, name, category, terms, null);
    }
  }

  public record RoleDefinition(
      long id, String name, String category, List<String> terms, String taxonomyVersion)
      implements TermDefinition {
    public RoleDefinition(long id, String name, String category, List<String> terms) {
      this(id, name, category, terms, null);
    }
  }

  public record DetectedSkill(
      long id,
      String name,
      String category,
      String matchedTerm,
      String evidence,
      BigDecimal confidence,
      String evidenceSection,
      String matchType,
      int startOffset,
      int endOffset,
      String taxonomyVersion,
      boolean selectedByDefault) {}

  public record DetectedRole(
      long id,
      String name,
      String category,
      String evidenceSource,
      String evidence,
      BigDecimal confidence,
      int priority,
      String matchType,
      int startOffset,
      int endOffset,
      String taxonomyVersion) {}

  public record TermSuggestion(
      String termKind,
      String normalizedTerm,
      String evidenceSection,
      String evidence,
      BigDecimal evidenceStrength,
      String reviewState,
      int priority) {}

  public record Extraction(
      List<DetectedSkill> skills, List<DetectedRole> roles, List<TermSuggestion> terms) {}

  private record TermHit<D extends TermDefinition>(
      D definition, String term, String surface, int start, int end, boolean canonical) {}

  private record IndexedTerm<D extends TermDefinition>(
      D definition, String term, boolean canonical) {}
}
