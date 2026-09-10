package com.ankit.joblens.onboarding;

import java.util.List;
import java.util.Optional;

// Second pass for résumé phrases the exact-phrase PhraseAutomaton pass didn't match. NOOP preserves
// today's exact-match-only behavior for callers/tests that construct the extractor without one.
public interface SemanticSkillMatcher {
  SemanticSkillMatcher NOOP = (phrase, candidates) -> Optional.empty();

  Optional<SemanticMatch> bestMatch(
      String phrase, List<? extends ProfileIntelligenceExtractor.TermDefinition> candidates);

  record SemanticMatch(long definitionId, double similarity) {}
}
