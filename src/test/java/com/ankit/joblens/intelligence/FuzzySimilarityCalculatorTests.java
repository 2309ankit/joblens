package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuzzySimilarityCalculatorTests {

  private final FuzzySimilarityCalculator calculator =
      new FuzzySimilarityCalculator(BigDecimal.valueOf(75), BigDecimal.valueOf(90));

  @Test
  void scoresExplainableLikelyDuplicateAcrossAvailableDimensions() {
    JobSimilarity similarity =
        calculator.calculate(
            job(
                1,
                "Senior Java Backend Engineer",
                "Example Bank",
                "Singapore",
                "Build payments APIs with Spring Boot and Kafka",
                "PERMANENT",
                "a"),
            job(
                2,
                "Java Backend Engineer",
                "Example Bank",
                "Singapore",
                "Build payment APIs using Spring Boot and Kafka",
                "PERMANENT",
                "b"));

    assertThat(similarity.overallScore()).isGreaterThanOrEqualTo(BigDecimal.valueOf(80));
    assertThat(similarity.decision()).isIn("POSSIBLE_DUPLICATE", "LIKELY_DUPLICATE");
    assertThat(similarity.explanation())
        .contains("title=", "company=100.00", "effectiveWeight=100");
    assertThat(calculator.meetsMinimum(similarity)).isTrue();
  }

  @Test
  void dynamicallyReweightsMissingOptionalFields() {
    JobSimilarity similarity =
        calculator.calculate(
            job(1, "Java Platform Engineer", null, null, "Spring Kafka platform", null, "a"),
            job(
                2,
                "Java Platform Engineer",
                null,
                null,
                "Spring Kafka platform services",
                null,
                "b"));

    assertThat(similarity.companyScore()).isNull();
    assertThat(similarity.locationScore()).isNull();
    assertThat(similarity.employmentScore()).isNull();
    assertThat(similarity.explanation()).contains("not-compared", "effectiveWeight=65");
  }

  @Test
  void blocksUnrelatedPairsBeforeFullComparison() {
    assertThat(
            calculator.isCandidate(
                job(1, "Java Backend Engineer", "Example Bank", null, null, null, "a"),
                job(2, "Digital Marketing Manager", "Retail Co", null, null, null, "b")))
        .isFalse();
  }

  @Test
  void validatesThresholdsAndPairOrder() {
    assertThatThrownBy(
            () -> new FuzzySimilarityCalculator(BigDecimal.valueOf(95), BigDecimal.valueOf(90)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    job(2, "Java", null, null, null, null, "a"),
                    job(1, "Java", null, null, null, null, "b")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static DuplicateJobView job(
      long id,
      String title,
      String company,
      String location,
      String description,
      String employmentType,
      String hashCharacter) {
    return new DuplicateJobView(
        id,
        "ADZUNA",
        "EXT-" + id,
        title,
        company,
        location,
        description,
        employmentType,
        hashCharacter.repeat(64));
  }
}
