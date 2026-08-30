package com.ankit.joblens.discovery;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class GreenhouseJobSourceClient implements JobSourceClient {
  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final GreenhouseProperties properties;

  public GreenhouseJobSourceClient(
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      GreenhouseProperties properties) {
    this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.GREENHOUSE;
  }

  @Override
  public JobPage search(SearchProfile profile, PageRequest request) {
    String boardToken = profile.sourceKey();
    if (boardToken == null || boardToken.isBlank()) {
      throw new JobSourceException(
          "Greenhouse profile " + profile.profileId() + " has no board token");
    }
    if (request.page() > 1) {
      return new JobPage(request.page(), 0, List.of(), false);
    }

    Mono<String> call =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .pathSegment("v1", "boards", boardToken, "jobs")
                        .queryParam("content", "true")
                        .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchangeToMono(
                response ->
                    handleResponse(response.statusCode(), response.bodyToMono(String.class)))
            .timeout(properties.timeout());
    if (properties.retryAttempts() > 1) {
      call =
          call.retryWhen(
              Retry.backoff(properties.retryAttempts() - 1, properties.retryBackoff())
                  .filter(this::isTransient)
                  .jitter(0));
    }
    try {
      String body = call.block();
      if (body == null) {
        throw new JobSourceException("Greenhouse returned no response body for " + boardToken);
      }
      return parse(body, profile, request.page());
    } catch (JobSourceException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new JobSourceException("Greenhouse request failed for board " + boardToken, exception);
    }
  }

  private Mono<String> handleResponse(HttpStatusCode statusCode, Mono<String> body) {
    if (statusCode.is2xxSuccessful()) {
      return body;
    }
    if (statusCode.value() == 429 || statusCode.is5xxServerError()) {
      return body.defaultIfEmpty("")
          .flatMap(
              ignored ->
                  Mono.error(
                      new TransientJobSourceException(
                          "Transient Greenhouse HTTP status " + statusCode.value())));
    }
    return body.defaultIfEmpty("")
        .flatMap(
            ignored ->
                Mono.error(
                    new JobSourceException(
                        "Non-retryable Greenhouse HTTP status " + statusCode.value())));
  }

  private boolean isTransient(Throwable throwable) {
    return throwable instanceof TransientJobSourceException
        || throwable instanceof WebClientRequestException
        || throwable instanceof TimeoutException;
  }

  private JobPage parse(String body, SearchProfile profile, int page) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode jobsNode = root == null ? null : root.get("jobs");
      if (jobsNode == null || !jobsNode.isArray()) {
        throw new MalformedJobSourceResponseException(
            "Greenhouse response is missing the jobs array");
      }
      List<RawSourceJob> jobs = new ArrayList<>();
      for (JsonNode job : jobsNode) {
        JsonNode id = job.get("id");
        if (id == null || id.asString().isBlank()) {
          throw new MalformedJobSourceResponseException("Greenhouse job is missing its id");
        }
        if (!matches(job, profile)) {
          continue;
        }
        String rawJson = job.toString();
        jobs.add(
            new RawSourceJob(
                profile.sourceKey() + ":" + id.asString(),
                text(job.get("absolute_url")),
                rawJson,
                sha256(rawJson)));
      }
      return new JobPage(page, jobsNode.size(), jobs, false);
    } catch (MalformedJobSourceResponseException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new MalformedJobSourceResponseException(
          "Greenhouse returned malformed JSON", exception);
    }
  }

  private static boolean matches(JsonNode job, SearchProfile profile) {
    String searchable =
        (text(job.get("title")) + " " + text(job.get("content"))).toLowerCase(Locale.ROOT);
    boolean keywordMatch =
        java.util.Arrays.stream(profile.keywords().toLowerCase(Locale.ROOT).split("\\s+"))
            .filter(token -> token.length() > 1)
            .anyMatch(searchable::contains);
    if (!keywordMatch) {
      return false;
    }
    String wantedLocation = profile.location();
    if (wantedLocation == null || wantedLocation.isBlank()) {
      return true;
    }
    JsonNode locationNode = job.get("location");
    String actualLocation =
        locationNode != null && locationNode.isObject()
            ? text(locationNode.get("name"))
            : text(locationNode);
    return actualLocation != null
        && actualLocation
            .toLowerCase(Locale.ROOT)
            .contains(wantedLocation.toLowerCase(Locale.ROOT));
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? "" : node.asString();
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
