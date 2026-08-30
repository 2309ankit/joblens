package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GreenhouseJobPostingNormalizerTests {
  private final GreenhouseJobPostingNormalizer normalizer =
      new GreenhouseJobPostingNormalizer(
          new ObjectMapper(), new HtmlTextCleaner(), new NormalizedContentHasher());

  @Test
  void normalizesPublicGreenhouseJob() {
    NormalizedJob job =
        normalizer.normalize(
            new RawJobPosting(
                1,
                "GREENHOUSE",
                "example:101",
                "https://fallback",
                "hash",
                """
                {"id":101,"title":"Senior Java Engineer",
                 "location":{"name":"Singapore"},
                 "content":"<p>Build <strong>Spring Boot</strong> services</p>",
                 "updated_at":"2026-08-30T02:00:00Z",
                 "absolute_url":"https://boards.example/jobs/101"}
                """));

    assertThat(job.source()).isEqualTo("GREENHOUSE");
    assertThat(job.title()).isEqualTo("Senior Java Engineer");
    assertThat(job.location()).isEqualTo("Singapore");
    assertThat(job.descriptionText()).isEqualTo("Build Spring Boot services");
    assertThat(job.sourceUrl()).isEqualTo("https://boards.example/jobs/101");
    assertThat(job.normalizedContentHash()).hasSize(64);
  }
}
