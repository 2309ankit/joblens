package com.ankit.joblens.onboarding;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.intelligence.embedding.CosineSimilarity;
import com.ankit.joblens.intelligence.embedding.TextEmbeddingModel;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

// Shared catalog is embedded once at startup (mirrors SkillExtractor's signature-cached warmup);
// workspace-private skills are embedded lazily on first use since they're per-workspace and small.
@Component
@ConditionalOnProperty(
    prefix = "joblens.onboarding.semantic-matching",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EmbeddingSemanticSkillMatcher implements SemanticSkillMatcher {
  private final TextEmbeddingModel model;
  private final JdbcTemplate jdbc;
  private final ConcurrentHashMap<Long, float[]> skillEmbeddings = new ConcurrentHashMap<>();

  public EmbeddingSemanticSkillMatcher(TextEmbeddingModel model, JdbcTemplate jdbc) {
    this.model = model;
    this.jdbc = jdbc;
  }

  @PostConstruct
  void warmSharedSkillCatalog() {
    jdbc.query(
        load("sql/onboarding/list-shared-skill-canonical-names.sql"),
        (RowCallbackHandler)
            resultSet ->
                skillEmbeddings.put(
                    resultSet.getLong("id"), model.embed(resultSet.getString("canonical_name"))));
  }

  @Override
  public Optional<SemanticMatch> bestMatch(
      String phrase, List<? extends ProfileIntelligenceExtractor.TermDefinition> candidates) {
    if (phrase == null || phrase.isBlank() || candidates.isEmpty()) {
      return Optional.empty();
    }
    float[] phraseEmbedding = model.embed(phrase);
    long bestId = -1;
    double bestScore = -1;
    for (ProfileIntelligenceExtractor.TermDefinition candidate : candidates) {
      float[] candidateEmbedding =
          skillEmbeddings.computeIfAbsent(candidate.id(), id -> model.embed(candidate.name()));
      double score = CosineSimilarity.of(phraseEmbedding, candidateEmbedding);
      if (score > bestScore) {
        bestScore = score;
        bestId = candidate.id();
      }
    }
    return bestId < 0 ? Optional.empty() : Optional.of(new SemanticMatch(bestId, bestScore));
  }
}
