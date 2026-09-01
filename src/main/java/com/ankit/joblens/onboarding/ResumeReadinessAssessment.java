package com.ankit.joblens.onboarding;

import java.time.OffsetDateTime;
import java.util.List;

public record ResumeReadinessAssessment(
    long profileVersionId,
    String assessmentVersion,
    String status,
    int score,
    String contentType,
    int extractedCharacterCount,
    int wordCount,
    OffsetDateTime acknowledgedAt,
    OffsetDateTime createdAt,
    List<Finding> findings) {

  public boolean acknowledgementRequired() {
    return "REVIEW_REQUIRED".equals(status) && acknowledgedAt == null;
  }

  public record Finding(
      String code,
      String category,
      String severity,
      String message,
      String remediation,
      String evidence,
      int scoreDeduction) {}
}
