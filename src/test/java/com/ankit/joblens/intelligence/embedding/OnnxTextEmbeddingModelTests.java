package com.ankit.joblens.intelligence.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Loads the real bundled all-MiniLM-L6-v2 ONNX model from the classpath (no Spring context) to
// sanity-check the embedding pipeline end to end and give real similarity numbers for S0.4
// threshold calibration; see S0_4_SEMANTIC_SKILL_EXTRACTION.md section 4.
class OnnxTextEmbeddingModelTests {
  private final OnnxTextEmbeddingModel model = new OnnxTextEmbeddingModel("");

  @Test
  void embedsTextIntoA384DimensionVector() {
    assertThat(model.embed("Outbound Prospecting")).hasSize(384);
  }

  @Test
  void scoresASynonymousPhraseHigherThanAnUnrelatedOne() {
    float[] outboundProspecting = model.embed("Outbound Prospecting");
    float[] coldOutreach = model.embed("Cold Outreach & Email Sequencing");
    float[] unrelated = model.embed("Chicken Alfredo Pasta Recipe");

    double synonymSimilarity = CosineSimilarity.of(outboundProspecting, coldOutreach);
    double unrelatedSimilarity = CosineSimilarity.of(outboundProspecting, unrelated);

    assertThat(synonymSimilarity).isGreaterThan(unrelatedSimilarity);
  }

  // Real observed similarity is ~0.50, below the provisional 0.55 suggest-threshold sketch in
  // NEXT_MILESTONES.md/S0_4_SEMANTIC_SKILL_EXTRACTION.md section 4 (a starter guess, not
  // calibrated) -
  // real evidence for the fixture-sweep calibration this checkpoint still owes before launch.
  @Test
  void scoresAZohoCrmParaphraseAsMeaningfullyRelatedButBelowTheProvisionalSuggestThreshold() {
    float[] zohoCrm = model.embed("Zoho CRM");
    float[] paraphrase = model.embed("managed pipeline in Zoho");
    float[] unrelated = model.embed("payroll tax filing deadlines");

    double paraphraseSimilarity = CosineSimilarity.of(zohoCrm, paraphrase);
    assertThat(paraphraseSimilarity).isGreaterThan(CosineSimilarity.of(zohoCrm, unrelated));
    assertThat(paraphraseSimilarity).isGreaterThan(0.35);
  }

  @Test
  void doesNotConfuseAGenericTrapPhraseWithAnUnrelatedCatalogSkill() {
    float[] machineLearning = model.embed("Machine Learning");
    float[] unrelated = model.embed("payroll tax filing deadlines");

    assertThat(CosineSimilarity.of(machineLearning, unrelated)).isLessThan(0.55);
  }
}
