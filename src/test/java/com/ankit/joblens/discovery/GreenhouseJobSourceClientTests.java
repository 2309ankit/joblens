package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class GreenhouseJobSourceClientTests {
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
  void readsPublicBoardAndFiltersByWorkspaceSearch() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"jobs":[
                  {"id":101,"title":"Senior Java Engineer","location":{"name":"Singapore"},
                   "content":"<p>Spring Boot and Kafka</p>","absolute_url":"https://boards.example/jobs/101"},
                  {"id":102,"title":"Sales Manager","location":{"name":"Singapore"},
                   "content":"Enterprise sales","absolute_url":"https://boards.example/jobs/102"},
                  {"id":103,"title":"Java Engineer","location":{"name":"London"},
                   "content":"Java","absolute_url":"https://boards.example/jobs/103"}
                ]}
                """));

    JobPage page = client().search(profile(), new PageRequest(1, 20));

    assertThat(page.jobs())
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.externalJobId()).isEqualTo("example:101");
              assertThat(job.sourceUrl()).isEqualTo("https://boards.example/jobs/101");
              assertThat(job.payloadHash()).hasSize(64);
            });
    assertThat(page.hasMore()).isFalse();
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/boards/example/jobs?content=true");
  }

  private GreenhouseJobSourceClient client() {
    var properties =
        new GreenhouseProperties(
            server.url("/").toString(), Duration.ofSeconds(2), 2, Duration.ofMillis(1));
    return new GreenhouseJobSourceClient(WebClient.builder(), new ObjectMapper(), properties);
  }

  private static SearchProfile profile() {
    return new SearchProfile(
        "GH1", "GREENHOUSE", "example", "java spring", "Singapore", "", "", "ANY", true);
  }
}
