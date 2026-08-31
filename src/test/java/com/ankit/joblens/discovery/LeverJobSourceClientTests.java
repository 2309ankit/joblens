package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class LeverJobSourceClientTests {
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
  void pagesPublicPostingsAndPreservesMatchingRawJson() throws Exception {
    server.enqueue(
        json(
            200,
            """
            [{"id":"one","text":"Senior Java Engineer","descriptionPlain":"Spring Boot",
              "categories":{"location":"Singapore"},"hostedUrl":"https://jobs.lever.co/example/one"},
             {"id":"two","text":"Sales Director","descriptionPlain":"Enterprise sales",
              "categories":{"location":"Singapore"},"hostedUrl":"https://jobs.lever.co/example/two"}]
            """));

    JobPage page = client(2).search(profile(), new PageRequest(2, 2));

    assertThat(page.hasMore()).isTrue();
    assertThat(page.jobs())
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.externalJobId()).isEqualTo("example:one");
              assertThat(job.sourceUrl()).isEqualTo("https://jobs.lever.co/example/one");
              assertThat(job.payloadHash()).hasSize(64);
            });
    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v0/postings/example?mode=json&skip=2&limit=2");
  }

  @Test
  void retriesTransientFailureAndRejectsInvalidShape() {
    server.enqueue(json(503, "provider unavailable"));
    server.enqueue(json(200, "[]"));

    assertThat(client(20).search(profile(), new PageRequest(1, 20)).jobs()).isEmpty();
    assertThat(server.getRequestCount()).isEqualTo(2);

    server.enqueue(json(200, "{}"));
    assertThatThrownBy(() -> client(20).search(profile(), new PageRequest(1, 20)))
        .isInstanceOf(MalformedJobSourceResponseException.class)
        .hasMessageContaining("JSON array");
  }

  private LeverJobSourceClient client(int pageSize) {
    return new LeverJobSourceClient(
        WebClient.builder(),
        new ObjectMapper(),
        new LeverProperties(
            server.url("/").toString(), Duration.ofSeconds(2), pageSize, 2, Duration.ofMillis(1)));
  }

  private static SearchProfile profile() {
    return new SearchProfile(
        "LV1", "LEVER", "example", "java spring", "Singapore", "", "", "ANY", true);
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
