package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class JoobleJobSourceClientTests {
  private MockWebServer server;
  private MockWebServer secondServer;

  @BeforeEach
  void startServer() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stopServer() throws Exception {
    server.shutdown();
    if (secondServer != null) {
      secondServer.shutdown();
    }
  }

  @Test
  void preservesRawJobsAndPostsSearchParameters() throws Exception {
    server.enqueue(
        json(
            200,
            """
            {"totalCount":21,"jobs":[{"id":101,"title":"Java Engineer",
            "location":"Singapore","snippet":"Spring Boot","link":"https://sg.jooble.org/jdp/101"}]}
            """));

    JobPage page = client(credential("sg", "api-key")).search(profile(), new PageRequest(1, 20));

    assertThat(page.totalCount()).isEqualTo(21);
    assertThat(page.hasMore()).isTrue();
    assertThat(page.jobs())
        .singleElement()
        .satisfies(job -> assertThat(job.payloadHash()).hasSize(64));
    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/api-key");
    assertThat(request.getBody().readUtf8())
        .contains("\"keywords\":\"java developer\"")
        .contains("\"location\":\"Singapore\"")
        .contains("\"ResultOnPage\":20");
  }

  @Test
  void failsAtExecutionTimeWhenCredentialsAreAbsent() {
    assertThatThrownBy(() -> client(credential("sg", "")).search(profile(), new PageRequest(1, 20)))
        .isInstanceOf(MissingJobSourceCredentialsException.class)
        .hasMessageContaining("JOOBLE_SG_API_KEY");
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void failsWithACountrySpecificMessageWhenThatCountryIsNotConfiguredAtAll() {
    SearchProfile malaysia =
        new SearchProfile("SP-JOOBLE-MY", "JOOBLE", "my", "data analyst", "", "", "", "ANY", true);

    assertThatThrownBy(
            () -> client(credential("sg", "api-key")).search(malaysia, new PageRequest(1, 20)))
        .isInstanceOf(MissingJobSourceCredentialsException.class)
        .hasMessageContaining("JOOBLE_MY_API_KEY");
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void routesEachConfiguredCountryToItsOwnServerAndCredential() throws Exception {
    secondServer = new MockWebServer();
    secondServer.start();
    server.enqueue(json(200, "{\"totalCount\":0,\"jobs\":[]}"));
    secondServer.enqueue(json(200, "{\"totalCount\":0,\"jobs\":[]}"));
    JoobleJobSourceClient client =
        client(
            new JoobleCountryCredential("sg", server.url("/").toString(), "sg-key"),
            new JoobleCountryCredential("my", secondServer.url("/").toString(), "my-key"));
    SearchProfile malaysia =
        new SearchProfile("SP-JOOBLE-MY", "JOOBLE", "my", "data analyst", "", "", "", "ANY", true);

    client.search(profile(), new PageRequest(1, 20));
    client.search(malaysia, new PageRequest(1, 20));

    assertThat(server.takeRequest().getPath()).isEqualTo("/api/sg-key");
    assertThat(secondServer.takeRequest().getPath()).isEqualTo("/api/my-key");
  }

  @Test
  void sendsTheCountryNameWhenTheUserChoosesACountryWideMarket() throws Exception {
    server.enqueue(json(200, "{\"totalCount\":0,\"jobs\":[]}"));
    SearchProfile countryWide =
        new SearchProfile("SP-JOOBLE-AU", "JOOBLE", "au", "data analyst", "", "", "", "ANY", true);

    client(credential("au", "api-key")).search(countryWide, new PageRequest(1, 20));

    assertThat(server.takeRequest().getBody().readUtf8()).contains("\"location\":\"Australia\"");
  }

  @Test
  void rejectsMalformedResponsesWithoutRetry() {
    server.enqueue(json(200, "{}"));

    assertThatThrownBy(
            () -> client(credential("sg", "api-key")).search(profile(), new PageRequest(1, 20)))
        .isInstanceOf(MalformedJobSourceResponseException.class)
        .hasMessageContaining("jobs array");
  }

  private JoobleCountryCredential credential(String countryCode, String apiKey) {
    return new JoobleCountryCredential(countryCode, server.url("/").toString(), apiKey);
  }

  private JoobleJobSourceClient client(JoobleCountryCredential... credentials) {
    return new JoobleJobSourceClient(
        WebClient.builder(),
        new ObjectMapper(),
        new JoobleProperties(
            List.of(credentials), Duration.ofSeconds(2), 20, 2, Duration.ofMillis(1)));
  }

  private static SearchProfile profile() {
    return new SearchProfile(
        "SP-JOOBLE", "JOOBLE", "sg", "java developer", "Singapore", "", "", "ANY", true);
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
