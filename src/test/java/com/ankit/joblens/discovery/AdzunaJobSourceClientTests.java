package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.time.Duration;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class AdzunaJobSourceClientTests {

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
  void preservesRawJobAndBuildsCurrentSearchRequest() throws Exception {
    server.enqueue(
        json(
            200,
            """
                {"count":1,"results":[{"id":"A-1","redirect_url":"https://example/jobs/1",
                "title":"Java Engineer","custom":{"keep":true}}]}
                """));

    JobPage page = client("id", "key", 3).search(profile(), new PageRequest(1, 20));

    assertThat(page.jobs()).hasSize(1);
    assertThat(page.jobs().getFirst().rawJson()).contains("\"custom\":{\"keep\":true}");
    assertThat(page.jobs().getFirst().payloadHash()).hasSize(64);
    var request = server.takeRequest();
    assertThat(request.getPath()).startsWith("/v1/api/jobs/sg/search/1?");
    assertThat(request.getRequestUrl().queryParameter("app_id")).isEqualTo("id");
    assertThat(request.getRequestUrl().queryParameter("app_key")).isEqualTo("key");
    assertThat(request.getRequestUrl().queryParameter("what")).isEqualTo("java developer");
    assertThat(request.getRequestUrl().queryParameter("where")).isEqualTo("Singapore");
    assertThat(request.getHeader("Accept")).contains("application/json");
  }

  @ParameterizedTest
  @MethodSource("transientStatuses")
  void retriesTransientStatusThenSucceeds(int status) {
    server.enqueue(json(status, "{}"));
    server.enqueue(json(200, "{\"count\":0,\"results\":[]}"));

    JobPage page = client("id", "key", 2).search(profile(), new PageRequest(1, 20));

    assertThat(page.jobs()).isEmpty();
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void doesNotRetryNonTransientClientError() {
    server.enqueue(json(401, "{\"error\":\"unauthorized\"}"));

    assertThatThrownBy(() -> client("id", "bad", 3).search(profile(), new PageRequest(1, 20)))
        .isInstanceOf(JobSourceException.class)
        .hasMessageContaining("Non-retryable Adzuna HTTP status 401");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void rejectsMalformedResponseWithoutRetry() {
    server.enqueue(json(200, "not-json"));

    assertThatThrownBy(() -> client("id", "key", 3).search(profile(), new PageRequest(1, 20)))
        .isInstanceOf(MalformedJobSourceResponseException.class)
        .hasMessageContaining("malformed JSON");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void missingCredentialsFailOnlyWhenSearchRuns() {
    AdzunaJobSourceClient client = client("", "", 3);

    assertThat(client.supports(JobSource.ADZUNA)).isTrue();
    assertThatThrownBy(() -> client.search(profile(), new PageRequest(1, 20)))
        .isInstanceOf(MissingJobSourceCredentialsException.class)
        .hasMessageContaining("ADZUNA_APP_ID");
    assertThat(server.getRequestCount()).isZero();
  }

  private AdzunaJobSourceClient client(String appId, String appKey, int attempts) {
    AdzunaProperties properties =
        new AdzunaProperties(
            appId,
            appKey,
            server.url("/v1/api").toString(),
            Duration.ofSeconds(2),
            10,
            20,
            attempts,
            Duration.ofMillis(1));
    return new AdzunaJobSourceClient(WebClient.builder(), new ObjectMapper(), properties);
  }

  private static SearchProfile profile() {
    return new SearchProfile(
        "SP001", "ADZUNA", "sg", "java developer", "Singapore", "java", "", "ANY", true);
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }

  private static Stream<Integer> transientStatuses() {
    return Stream.of(429, 500, 503);
  }
}
