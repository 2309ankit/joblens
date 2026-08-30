package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JoobleJobPostingNormalizerTests {
  private final JoobleJobPostingNormalizer normalizer =
      new JoobleJobPostingNormalizer(
          new ObjectMapper(), new HtmlTextCleaner(), new NormalizedContentHasher());

  @Test
  void normalizesOfficialJoobleSearchFields() {
    NormalizedJob job =
        normalizer.normalize(
            new RawJobPosting(
                1,
                "JOOBLE",
                "101",
                "https://fallback",
                "hash",
                """
                {"id":101,"title":"Senior Java Engineer","company":"Example Bank",
                 "location":"Singapore","snippet":"<p>Spring Boot and Kafka</p>",
                 "type":"Full-time","updated":"2026-08-30T02:00:00Z",
                 "link":"https://sg.jooble.org/jdp/101"}
                """));

    assertThat(job.source()).isEqualTo("JOOBLE");
    assertThat(job.company()).isEqualTo("Example Bank");
    assertThat(job.employmentType()).isEqualTo("PERMANENT");
    assertThat(job.descriptionText()).isEqualTo("Spring Boot and Kafka");
    assertThat(job.sourceUrl()).isEqualTo("https://sg.jooble.org/jdp/101");
  }
}
