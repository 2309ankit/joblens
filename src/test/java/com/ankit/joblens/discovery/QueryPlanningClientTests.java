package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class QueryPlanningClientTests {
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
  void sendsOpenAiCompatibleRequestAndParsesBoundedPlan() throws Exception {
    server.enqueue(
        json(
            200,
            """
            {"choices":[{"message":{"content":"{\\"queryText\\":\\"Backend Engineer Java\\",\\"maxPages\\":3,\\"rationale\\":\\"Dropped a rare tool name that returned zero results last time.\\"}"}}],
             "usage":{"prompt_tokens":80,"completion_tokens":20}}
            """));

    QueryPlanResult result = client().plan(candidate());

    assertThat(result.queryText()).isEqualTo("Backend Engineer Java");
    assertThat(result.maxPages()).isEqualTo(3);
    assertThat(result.rationale()).contains("zero results");
    assertThat(result.inputTokens()).isEqualTo(80);
    assertThat(result.outputTokens()).isEqualTo(20);
    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
    assertThat(request.getBody().readUtf8())
        .contains(
            "nvidia/test-model",
            "Backend Engineer Java Apache Camel IBM MQ",
            "response_format",
            "\"chat_template_kwargs\":{\"enable_thinking\":false}",
            "previousStatus",
            "EMPTY");
  }

  @Test
  void rejectsANullContentTypicalOfAnExhaustedReasoningBudget() {
    server.enqueue(
        json(
            200,
            """
            {"choices":[{"message":{"content":null}}],
             "usage":{"prompt_tokens":80,"completion_tokens":300}}
            """));

    assertThatThrownBy(() -> client().plan(candidate()))
        .isInstanceOf(QueryPlanningException.class)
        .hasMessageContaining("choices[0].message.content must be text");
  }

  @Test
  void rejectsAMaxPagesAboveTheConfiguredCeiling() {
    server.enqueue(
        json(
            200,
            """
            {"choices":[{"message":{"content":"{\\"queryText\\":\\"Backend Engineer\\",\\"maxPages\\":99,\\"rationale\\":\\"Go deep.\\"}"}}]}
            """));

    assertThatThrownBy(() -> client().plan(candidate()))
        .isInstanceOf(QueryPlanningException.class)
        .hasMessageContaining("maxPages is outside");
  }

  @Test
  void doesNotExposeProviderResponseBodyOnHttpFailure() {
    server.enqueue(json(401, "{\"secret\":\"provider detail\"}"));

    assertThatThrownBy(() -> client().plan(candidate()))
        .isInstanceOf(QueryPlanningException.class)
        .hasMessage("Nebius HTTP status 401")
        .hasMessageNotContaining("provider detail");
  }

  private QueryPlanningClient client() {
    return new QueryPlanningClient(
        WebClient.builder(),
        new ObjectMapper(),
        new LlmQueryPlanningProperties(
            true,
            server.url("/v1").toString(),
            "test-key",
            "nvidia/test-model",
            Duration.ofSeconds(2),
            5,
            20,
            300,
            5,
            "test-plan-v1"));
  }

  private static QueryPlanCandidate candidate() {
    return new QueryPlanCandidate(
        "w-abc-adzuna-123", "ADZUNA", "Backend Engineer Java Apache Camel IBM MQ", 5, "EMPTY", 0);
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
