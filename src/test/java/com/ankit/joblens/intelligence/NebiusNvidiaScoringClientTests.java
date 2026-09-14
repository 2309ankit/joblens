package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class NebiusNvidiaScoringClientTests {
  private MockWebServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stopServer() throws Exception {
    server.shutdown();
  }

  @Test
  void sendsOpenAiCompatibleRequestAndParsesBoundedScore() throws Exception {
    server.enqueue(
        json(
            200,
            """
            {"choices":[{"message":{"content":"{\\"totalScore\\":87,\\"confidence\\":0.82,\\"qualifiesRecommended\\":true,\\"summary\\":\\"Strong backend fit\\",\\"reasons\\":[{\\"category\\":\\"skills\\",\\"explanation\\":\\"Java and PostgreSQL match\\"}]}"}}],
             "usage":{"prompt_tokens":120,"completion_tokens":40}}
            """));

    NvidiaScoreResult result = client().score(scoringCandidate(), context());

    assertThat(result.totalScore()).isEqualTo(87);
    assertThat(result.confidence()).isEqualTo(0.82);
    assertThat(result.qualifiesRecommended()).isTrue();
    assertThat(result.reasons()).hasSize(1);
    assertThat(result.inputTokens()).isEqualTo(120);
    assertThat(result.outputTokens()).isEqualTo(40);
    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    assertThat(request.getBody().readUtf8())
        .contains("nvidia/test-model", "Java Engineer", "response_format")
        .doesNotContain("original résumé");
  }

  @Test
  void rejectsOutOfRangeModelOutput() {
    server.enqueue(
        json(
            200,
            """
            {"choices":[{"message":{"content":"{\\"totalScore\\":101,\\"confidence\\":0.8,\\"qualifiesRecommended\\":true,\\"summary\\":\\"Fit\\",\\"reasons\\":[{\\"category\\":\\"skills\\",\\"explanation\\":\\"Match\\"}]}"}}]}
            """));

    assertThatThrownBy(() -> client().score(scoringCandidate(), context()))
        .isInstanceOf(NvidiaScoringException.class)
        .hasMessageContaining("totalScore is outside");
  }

  @Test
  void doesNotExposeProviderResponseBodyOnHttpFailure() {
    server.enqueue(json(401, "{\"secret\":\"provider detail\"}"));

    assertThatThrownBy(() -> client().score(scoringCandidate(), context()))
        .isInstanceOf(NvidiaScoringException.class)
        .hasMessage("Nebius HTTP status 401")
        .hasMessageNotContaining("provider detail");
  }

  private NebiusNvidiaScoringClient client() {
    return new NebiusNvidiaScoringClient(
        WebClient.builder(),
        new ObjectMapper(),
        new NvidiaRankingProperties(
            true,
            server.url("/v1").toString(),
            "test-key",
            "nvidia/test-model",
            Duration.ofSeconds(2),
            25,
            200,
            500,
            "test-prompt-v1"));
  }

  private static NvidiaScoringCandidate scoringCandidate() {
    return new NvidiaScoringCandidate(
        new NormalizedJobView(
            10,
            "JOOBLE",
            "external-10",
            "Java Engineer",
            "Example",
            "Singapore",
            "Build backend services with Java and PostgreSQL.",
            "FULL_TIME",
            BigDecimal.valueOf(90000),
            BigDecimal.valueOf(120000),
            "SGD",
            "HYBRID",
            OffsetDateTime.now(),
            "https://example.test/job/10",
            "a".repeat(64)),
        78,
        true,
        3L);
  }

  private static RoleRankingContext context() {
    CandidateProfileConfig candidate =
        new CandidateProfileConfig(
            2,
            "Singapore",
            List.of(new CandidateProfileConfig.LocationPreference("sg", "Singapore")),
            Set.of("Backend Engineer"),
            Set.of("Technology"),
            Map.of(1L, new CandidateProfileConfig.CandidateSkill("Java", "PRODUCTION", 1)),
            Map.of("work.arrangement", "HYBRID"));
    return new RoleRankingContext(candidate, List.of());
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
