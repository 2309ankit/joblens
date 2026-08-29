package com.ankit.joblens.intelligence;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdzunaJobPostingNormalizerTests {

    private final HtmlTextCleaner cleaner = new HtmlTextCleaner();
    private final NormalizedContentHasher hasher = new NormalizedContentHasher();
    private final AdzunaJobPostingNormalizer normalizer =
            new AdzunaJobPostingNormalizer(new ObjectMapper(), cleaner, hasher);

    @Test
    void extractsAdzunaFieldsAndCleansHtml() {
        NormalizedJob job = normalize("""
                {"id":"A1","title":" Senior Java Developer ",
                 "company":{"display_name":"Example Bank"},
                 "location":{"display_name":"Singapore"},
                 "description":"<p>Senior <strong>Java</strong> Developer &amp; owner</p><ul><li>Spring Boot</li><li>Kafka</li></ul>",
                 "contract_type":"permanent","salary_min":90000.00,"salary_max":120000,
                 "salary_currency":"sgd","remote_type":"hybrid",
                 "created":"2026-08-20T10:15:30Z","redirect_url":"https://example/jobs/A1"}
                """);

        assertThat(job.title()).isEqualTo("Senior Java Developer");
        assertThat(job.company()).isEqualTo("Example Bank");
        assertThat(job.location()).isEqualTo("Singapore");
        assertThat(job.descriptionText())
                .isEqualTo("Senior Java Developer & owner Spring Boot Kafka");
        assertThat(job.employmentType()).isEqualTo("PERMANENT");
        assertThat(job.salaryMin()).isEqualByComparingTo(new BigDecimal("90000.00"));
        assertThat(job.salaryMax()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(job.salaryCurrency()).isEqualTo("SGD");
        assertThat(job.remoteType()).isEqualTo("HYBRID");
        assertThat(job.postedAt()).hasToString("2026-08-20T10:15:30Z");
    }

    @Test
    void permitsMissingOptionalFields() {
        NormalizedJob job = normalize("{" + "\"title\":\"Backend Engineer\"}");

        assertThat(job.company()).isNull();
        assertThat(job.location()).isNull();
        assertThat(job.salaryMin()).isNull();
        assertThat(job.salaryMax()).isNull();
        assertThat(job.employmentType()).isNull();
        assertThat(job.remoteType()).isNull();
    }

    @Test
    void rejectsMissingTitleAndMalformedJson() {
        assertThatThrownBy(() -> normalize("{\"description\":\"No title\"}"))
                .isInstanceOf(NormalizationRejectedException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> normalize("not-json"))
                .isInstanceOf(NormalizationRejectedException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void normalizedHashIgnoresJsonOrderAndVolatileMetadata() {
        NormalizedJob first = normalize("""
                {"title":"Java Engineer","description":"<p>Build APIs</p>",
                 "company":{"display_name":"Bank"},"location":{"display_name":"Singapore"},
                 "created":"2026-01-01T00:00:00Z","redirect_url":"https://example/one","id":"A1"}
                """);
        NormalizedJob reordered = normalize("""
                {"id":"different","redirect_url":"https://example/two","created":"2026-08-29T00:00:00Z",
                 "location":{"display_name":"Singapore"},"company":{"display_name":"Bank"},
                 "description":"<div>Build APIs</div>","title":"Java Engineer","volatile":"ignored"}
                """);

        assertThat(reordered.normalizedContentHash()).isEqualTo(first.normalizedContentHash());
        assertThat(normalizer.normalize(raw(99, firstJson())).normalizedContentHash())
                .isEqualTo(normalizer.normalize(raw(99, firstJson())).normalizedContentHash());
    }

    private NormalizedJob normalize(String json) {
        return normalizer.normalize(raw(1, json));
    }

    private static RawJobPosting raw(long id, String json) {
        return new RawJobPosting(id, "ADZUNA", "A1", "https://fallback", "hash", json);
    }

    private static String firstJson() {
        return "{\"title\":\"Java Engineer\",\"description\":\"Build APIs\"}";
    }
}
