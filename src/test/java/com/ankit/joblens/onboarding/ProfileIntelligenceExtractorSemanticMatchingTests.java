package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfileIntelligenceExtractorSemanticMatchingTests {
  private static final ProfileIntelligenceExtractor.SkillDefinition OUTBOUND_PROSPECTING =
      new ProfileIntelligenceExtractor.SkillDefinition(
          10, "Outbound Prospecting", "SALES", List.of("Outbound Prospecting"));
  private static final ProfileIntelligenceExtractor.SkillDefinition PYTHON =
      new ProfileIntelligenceExtractor.SkillDefinition(11, "Python", "DATA", List.of("Python"));

  private static final String RESUME =
      """
      Profile
      Skills
      Cold Email Outreach, Python
      """;

  @Test
  void autoAcceptsAHighConfidenceSemanticMatchAsADetectedSkill() {
    var extractor = extractorWithFixedSimilarity(Map.of("Cold Email Outreach", 0.85), 0.80, 0.55);

    var result = extractor.extract(RESUME, List.of(OUTBOUND_PROSPECTING, PYTHON), List.of());

    assertThat(result.skills())
        .extracting(
            ProfileIntelligenceExtractor.DetectedSkill::name,
            ProfileIntelligenceExtractor.DetectedSkill::matchType,
            ProfileIntelligenceExtractor.DetectedSkill::matchedTerm)
        .contains(tuple("Outbound Prospecting", "SEMANTIC", "Cold Email Outreach"));
    assertThat(result.terms())
        .extracting(ProfileIntelligenceExtractor.TermSuggestion::normalizedTerm)
        .doesNotContain("Cold Email Outreach");
  }

  @Test
  void surfacesAMidConfidenceSemanticMatchAsACatalogMappedSuggestion() {
    var extractor = extractorWithFixedSimilarity(Map.of("Cold Email Outreach", 0.65), 0.80, 0.55);

    var result = extractor.extract(RESUME, List.of(OUTBOUND_PROSPECTING, PYTHON), List.of());

    assertThat(result.skills())
        .extracting(ProfileIntelligenceExtractor.DetectedSkill::name)
        .doesNotContain("Outbound Prospecting");
    assertThat(result.terms())
        .filteredOn(term -> term.normalizedTerm().equals("Cold Email Outreach"))
        .singleElement()
        .satisfies(
            term -> {
              assertThat(term.reviewState()).isEqualTo("SUGGESTED");
              assertThat(term.matchedCanonicalTerm()).isEqualTo("Outbound Prospecting");
            });
  }

  @Test
  void leavesALowConfidenceMatchAsAnUnmappedSuggestion() {
    var extractor = extractorWithFixedSimilarity(Map.of("Cold Email Outreach", 0.20), 0.80, 0.55);

    var result = extractor.extract(RESUME, List.of(OUTBOUND_PROSPECTING, PYTHON), List.of());

    assertThat(result.terms())
        .filteredOn(term -> term.normalizedTerm().equals("Cold Email Outreach"))
        .singleElement()
        .satisfies(
            term -> {
              assertThat(term.reviewState()).isEqualTo("SUGGESTED");
              assertThat(term.matchedCanonicalTerm()).isNull();
            });
  }

  @Test
  void excludesAlreadyExactlyMatchedSkillsFromTheSemanticCandidatePool() {
    var candidateIdsSeen = new ArrayList<List<Long>>();
    SemanticSkillMatcher recordingMatcher =
        (phrase, candidates) -> {
          candidateIdsSeen.add(
              candidates.stream().map(ProfileIntelligenceExtractor.TermDefinition::id).toList());
          return Optional.empty();
        };
    var extractor = new ProfileIntelligenceExtractor(recordingMatcher, 0.80, 0.55);

    extractor.extract(RESUME, List.of(OUTBOUND_PROSPECTING, PYTHON), List.of());

    assertThat(candidateIdsSeen).isNotEmpty();
    assertThat(candidateIdsSeen).allSatisfy(ids -> assertThat(ids).doesNotContain(PYTHON.id()));
    assertThat(candidateIdsSeen)
        .anySatisfy(ids -> assertThat(ids).contains(OUTBOUND_PROSPECTING.id()));
  }

  private static ProfileIntelligenceExtractor extractorWithFixedSimilarity(
      Map<String, Double> similarityByPhrase, double autoAccept, double suggest) {
    SemanticSkillMatcher matcher =
        (phrase, candidates) -> {
          Double similarity = similarityByPhrase.get(phrase);
          if (similarity == null || candidates.isEmpty()) {
            return Optional.empty();
          }
          return Optional.of(
              new SemanticSkillMatcher.SemanticMatch(candidates.get(0).id(), similarity));
        };
    return new ProfileIntelligenceExtractor(matcher, autoAccept, suggest);
  }

  private static org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.groups.Tuple.tuple(values);
  }
}
