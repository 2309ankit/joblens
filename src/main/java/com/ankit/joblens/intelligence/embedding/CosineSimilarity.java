package com.ankit.joblens.intelligence.embedding;

public final class CosineSimilarity {
  private CosineSimilarity() {}

  public static double of(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new IllegalArgumentException("Embeddings must share the same dimensionality");
    }
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) {
      return 0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
