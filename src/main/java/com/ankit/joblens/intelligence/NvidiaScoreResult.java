package com.ankit.joblens.intelligence;

import java.util.List;

public record NvidiaScoreResult(
    int totalScore,
    double confidence,
    boolean qualifiesRecommended,
    String summary,
    List<Reason> reasons,
    Integer inputTokens,
    Integer outputTokens) {

  public record Reason(String category, String explanation) {}
}
