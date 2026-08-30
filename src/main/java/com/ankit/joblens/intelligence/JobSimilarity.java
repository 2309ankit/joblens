package com.ankit.joblens.intelligence;

import java.math.BigDecimal;

public record JobSimilarity(
    long leftJobId,
    long rightJobId,
    String algorithmVersion,
    BigDecimal overallScore,
    BigDecimal titleScore,
    BigDecimal descriptionScore,
    BigDecimal companyScore,
    BigDecimal locationScore,
    BigDecimal employmentScore,
    String decision,
    String explanation,
    String leftContentHash,
    String rightContentHash) {

  String pairKey() {
    return leftJobId + ":" + rightJobId;
  }
}
