package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FuzzyCandidateIndexTests {
  private final FuzzySimilarityCalculator calculator =
      new FuzzySimilarityCalculator(BigDecimal.valueOf(75), BigDecimal.valueOf(90));
  private final LegacyFuzzySimilarityCalculator legacy =
      new LegacyFuzzySimilarityCalculator(BigDecimal.valueOf(75), BigDecimal.valueOf(90));

  @Test
  void indexedCandidatesAndEveryScoreMatchFrozenExhaustiveAlgorithm() {
    String[] titles = {
      "Java backend engineer",
      "Senior Java Backend Engineer",
      "Ｃ＋＋ developer",
      "Data Scientist",
      "Data Science",
      "Sales executive",
      "Sales manager",
      "Engineer",
      "Engineering",
      "",
      null,
      "東京 エンジニア",
      "abc def ghi",
      "abc def ghx"
    };
    String[] companies = {"Acme", "ACME", "Acme Ltd.", "Other", "", null, "!!"};
    String[] texts = {
      null, "", "the and", "Spring Kafka payment APIs", "Spring Boot payments API", "東京"
    };
    Random random = new Random(42);
    List<DuplicateJobView> source = new ArrayList<>();
    for (int i = 0; i < 120; i++) {
      source.add(
          new DuplicateJobView(
              i + 1,
              "ADZUNA",
              "test-" + i,
              titles[random.nextInt(titles.length)],
              companies[random.nextInt(companies.length)],
              texts[random.nextInt(texts.length)],
              texts[random.nextInt(texts.length)],
              texts[random.nextInt(texts.length)],
              "a".repeat(64)));
    }
    var jobs = source.stream().map(calculator::prepare).toList();
    var index = new FuzzyCandidateIndex(jobs);
    for (int left = 0; left < jobs.size(); left++) {
      var expected = new TreeSet<Integer>();
      for (int right = left + 1; right < jobs.size(); right++) {
        if (legacy.isCandidate(source.get(left), source.get(right))) expected.add(right);
        assertThat(calculator.calculate(jobs.get(left), jobs.get(right)))
            .isEqualTo(legacy.calculate(source.get(left), source.get(right)));
      }
      assertThat(index.candidates(left)).isEqualTo(expected);
    }
  }

  @Test
  void indexesFiveThousandJobsWithinTenSecondsWithoutEmittingAllPairs() {
    assertTimeout(
        Duration.ofSeconds(10),
        () -> {
          List<DuplicateJobView> source = new ArrayList<>();
          Random random = new Random(73);
          for (int i = 0; i < 5000; i++) {
            source.add(
                new DuplicateJobView(
                    i + 1,
                    "ADZUNA",
                    "test-" + i,
                    Long.toUnsignedString(random.nextLong(), 36),
                    "Company " + i,
                    "Singapore",
                    "Build reliable systems with Java and Spring. ".repeat(100),
                    "PERMANENT",
                    "a".repeat(64)));
          }
          var jobs = source.stream().map(calculator::prepare).toList();
          var index = new FuzzyCandidateIndex(jobs);
          long candidates = 0;
          for (int left = 0; left < jobs.size(); left++)
            candidates += index.candidates(left).size();
          assertThat(candidates).isLessThan(5000);
        });
  }
}
