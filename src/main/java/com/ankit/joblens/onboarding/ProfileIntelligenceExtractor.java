package com.ankit.joblens.onboarding;

import com.ankit.joblens.intelligence.PhraseAutomaton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileIntelligenceExtractor {
  public static final String EXTRACTOR_VERSION = "esco-semantic-v5";
  private static final String MATCH_TYPE_SEMANTIC = "SEMANTIC";

  private static final Pattern EXPLICIT_GROUP_LABEL =
      Pattern.compile("\\b([A-Z][A-Za-z/&-]*(?: [A-Z&][A-Za-z/&-]*){1,5}):");
  private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z][A-Z0-9+.-]{2,}\\b");
  private static final Pattern CONTACT_NOISE =
      Pattern.compile(
          "(?i)(https?://|www\\.|\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b|(?:blog|portfolio|website|linkedin|github|medium|email|phone|mobile)\\s*:|\\.com(?:/|\\b))");
  private static final Set<String> CANDIDATE_STOP_WORDS =
      Set.of("and", "or", "with", "skills", "competencies", "tools", "ecosystems");

  private final ResumeDocumentParser documentParser = new ResumeDocumentParser();
  private final SemanticSkillMatcher semanticMatcher;
  private final double semanticAutoAcceptThreshold;
  private final double semanticSuggestThreshold;

  @Autowired
  public ProfileIntelligenceExtractor(
      SemanticSkillMatcher semanticMatcher,
      @Value("${joblens.onboarding.semantic-matching.auto-accept-threshold:0.80}")
          double semanticAutoAcceptThreshold,
      @Value("${joblens.onboarding.semantic-matching.suggest-threshold:0.55}")
          double semanticSuggestThreshold) {
    this.semanticMatcher = semanticMatcher;
    this.semanticAutoAcceptThreshold = semanticAutoAcceptThreshold;
    this.semanticSuggestThreshold = semanticSuggestThreshold;
  }

  // Exact-phrase matching only, no semantic pass; pre-S0.4 behavior for non-Spring callers/tests.
  public ProfileIntelligenceExtractor() {
    this(SemanticSkillMatcher.NOOP, 0.80, 0.55);
  }

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

    List<SkillDefinition> unmatchedSkills =
        skills.stream()
            .filter(skill -> detectedSkills.stream().noneMatch(hit -> hit.id() == skill.id()))
            .toList();
    ExplicitCandidateOutcome candidateOutcome =
        explicitCandidates(document, detectedSkills, rankedRoles, rejected, unmatchedSkills);
    detectedSkills.addAll(candidateOutcome.semanticSkills());
    detectedSkills.sort(Comparator.comparing(DetectedSkill::name));

    var allTerms = new ArrayList<TermSuggestion>();
    allTerms.addAll(candidateOutcome.terms());
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
                          0,
                          null));
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

  private ExplicitCandidateOutcome explicitCandidates(
      ResumeDocument document,
      List<DetectedSkill> skills,
      List<DetectedRole> roles,
      List<TermSuggestion> rejected,
      List<SkillDefinition> unmatchedSkills) {
    Set<String> known = new LinkedHashSet<>();
    skills.forEach(
        skill -> {
          known.add(key(skill.name()));
          known.add(key(skill.matchedTerm()));
        });
    roles.forEach(role -> known.add(key(role.name())));
    rejected.forEach(term -> known.add(key(term.normalizedTerm())));
    var candidates = new LinkedHashMap<String, TermSuggestion>();
    var semanticSkills = new ArrayList<DetectedSkill>();
    var promoted = new LinkedHashSet<Long>();
    for (ResumeDocument.Segment segment : document.segments()) {
      if (!segment.section().explicitlyListsSkills()) {
        continue;
      }
      String grouped = EXPLICIT_GROUP_LABEL.matcher(segment.text()).replaceAll("$1;");
      for (String rawCandidate : grouped.split("[,;]|\\s+&\\s+")) {
        considerCandidate(
            candidates, semanticSkills, promoted, known, segment, rawCandidate, unmatchedSkills);
        Matcher acronyms = ACRONYM.matcher(rawCandidate);
        while (acronyms.find()) {
          considerCandidate(
              candidates,
              semanticSkills,
              promoted,
              known,
              segment,
              acronyms.group(),
              unmatchedSkills);
        }
        int open = rawCandidate.indexOf('(');
        int close = rawCandidate.indexOf(')', open + 1);
        if (open >= 0 && close > open) {
          considerCandidate(
              candidates,
              semanticSkills,
              promoted,
              known,
              segment,
              rawCandidate.substring(0, open),
              unmatchedSkills);
          for (String nested : rawCandidate.substring(open + 1, close).split("[/,]")) {
            considerCandidate(
                candidates, semanticSkills, promoted, known, segment, nested, unmatchedSkills);
          }
        }
      }
    }
    return new ExplicitCandidateOutcome(
        List.copyOf(semanticSkills), List.copyOf(candidates.values()));
  }

  private void considerCandidate(
      Map<String, TermSuggestion> candidates,
      List<DetectedSkill> semanticSkills,
      Set<Long> promoted,
      Set<String> known,
      ResumeDocument.Segment segment,
      String rawCandidate,
      List<SkillDefinition> unmatchedSkills) {
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
        || candidates.containsKey(normalized)
        || candidate.matches(".*[.!?].*[.!?].*")) {
      return;
    }
    String evidence =
        segment.text().length() <= 300 ? segment.text() : segment.text().substring(0, 297) + "...";
    List<SkillDefinition> stillUnmatched =
        unmatchedSkills.stream().filter(skill -> !promoted.contains(skill.id())).toList();
    Optional<SemanticSkillMatcher.SemanticMatch> match =
        semanticMatcher.bestMatch(candidate, stillUnmatched);
    if (match.isPresent() && match.get().similarity() >= semanticAutoAcceptThreshold) {
      SkillDefinition matched = matchedSkill(stillUnmatched, match.get().definitionId());
      promoted.add(matched.id());
      known.add(key(matched.name()));
      known.add(normalized);
      semanticSkills.add(
          new DetectedSkill(
              matched.id(),
              matched.name(),
              matched.category(),
              candidate,
              evidence,
              similarityConfidence(match.get().similarity()),
              segment.section().name(),
              MATCH_TYPE_SEMANTIC,
              0,
              0,
              matched.taxonomyVersion(),
              false));
      return;
    }
    if (match.isPresent() && match.get().similarity() >= semanticSuggestThreshold) {
      SkillDefinition matched = matchedSkill(stillUnmatched, match.get().definitionId());
      candidates.put(
          normalized,
          new TermSuggestion(
              "SKILL",
              candidate,
              segment.section().name(),
              evidence,
              similarityConfidence(match.get().similarity()),
              "SUGGESTED",
              0,
              matched.name()));
      return;
    }
    candidates.put(
        normalized,
        new TermSuggestion(
            "SKILL",
            candidate,
            segment.section().name(),
            evidence,
            new BigDecimal("0.750"),
            "SUGGESTED",
            0,
            null));
  }

  private static SkillDefinition matchedSkill(List<SkillDefinition> candidates, long id) {
    return candidates.stream()
        .filter(skill -> skill.id() == id)
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Semantic match resolved to an unknown skill"));
  }

  private static BigDecimal similarityConfidence(double similarity) {
    return BigDecimal.valueOf(Math.max(0, Math.min(1, similarity)))
        .setScale(3, RoundingMode.HALF_UP);
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
              priority++,
              term.matchedCanonicalTerm()));
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
      int priority,
      String matchedCanonicalTerm) {}

  public record Extraction(
      List<DetectedSkill> skills, List<DetectedRole> roles, List<TermSuggestion> terms) {}

  private record ExplicitCandidateOutcome(
      List<DetectedSkill> semanticSkills, List<TermSuggestion> terms) {}

  private record TermHit<D extends TermDefinition>(
      D definition, String term, String surface, int start, int end, boolean canonical) {}

  private record IndexedTerm<D extends TermDefinition>(
      D definition, String term, boolean canonical) {}
}
