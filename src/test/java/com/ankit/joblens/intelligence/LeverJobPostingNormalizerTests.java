package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LeverJobPostingNormalizerTests {
  private final LeverJobPostingNormalizer normalizer =
      new LeverJobPostingNormalizer(
          new ObjectMapper(), new HtmlTextCleaner(), new NormalizedContentHasher());

  @Test
  void normalizesDocumentedPublicPostingFields() {
    NormalizedJob job =
        normalizer.normalize(
            new RawJobPosting(
                1,
                "LEVER",
                "example:abc",
                "https://fallback",
                "hash",
                """
                {"id":"abc","text":"Senior Java Engineer",
                 "categories":{"location":"Singapore","commitment":"Full-time"},
                 "description":"<p>Build Spring Boot services</p>",
                 "lists":[{"text":"Requirements","content":"<li>Java 21</li>"}],
                 "additional":"<p>Hybrid team</p>",
                 "hostedUrl":"https://jobs.lever.co/example/abc","workplaceType":"hybrid",
                 "salaryRange":{"currency":"sgd","interval":"year","min":120000,"max":160000}}
                """));

    assertThat(job.source()).isEqualTo("LEVER");
    assertThat(job.title()).isEqualTo("Senior Java Engineer");
    assertThat(job.location()).isEqualTo("Singapore");
    assertThat(job.employmentType()).isEqualTo("PERMANENT");
    assertThat(job.descriptionText())
        .isEqualTo("Build Spring Boot services Requirements Java 21 Hybrid team");
    assertThat(job.remoteType()).isEqualTo("HYBRID");
    assertThat(job.salaryMin()).isEqualByComparingTo(new BigDecimal("120000"));
    assertThat(job.salaryMax()).isEqualByComparingTo(new BigDecimal("160000"));
    assertThat(job.salaryCurrency()).isEqualTo("SGD");
    assertThat(job.sourceUrl()).isEqualTo("https://jobs.lever.co/example/abc");
    assertThat(job.normalizedContentHash()).hasSize(64);
  }
}
