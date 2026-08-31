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

public class LeverJobSourceClient implements JobSourceClient {
  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final LeverProperties properties;

  public LeverJobSourceClient(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, LeverProperties properties) {
    this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.LEVER;
  }

  @Override
  public JobPage search(SearchProfile profile, PageRequest request) {
    if (profile.sourceKey() == null || profile.sourceKey().isBlank()) {
      throw new JobSourceException("Lever profile " + profile.profileId() + " has no site name");
    }
    Mono<String> call =
        webClient
            .get()
            .uri(
                builder ->
                    builder
                        .pathSegment("v0", "postings", profile.sourceKey())
                        .queryParam("mode", "json")
                        .queryParam("skip", (request.page() - 1) * request.pageSize())
                        .queryParam("limit", request.pageSize())
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
        throw new JobSourceException("Lever returned no response body for " + profile.sourceKey());
      }
      return parse(body, profile, request);
    } catch (JobSourceException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new JobSourceException(
          "Lever request failed for site " + profile.sourceKey(), exception);
    }
  }

  @Override
  public int pageSize() {
    return properties.pageSize();
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
                          "Transient Lever HTTP status " + statusCode.value())));
    }
    return body.defaultIfEmpty("")
        .flatMap(
            ignored ->
                Mono.error(
                    new JobSourceException(
                        "Non-retryable Lever HTTP status " + statusCode.value())));
  }

  private boolean isTransient(Throwable throwable) {
    return throwable instanceof TransientJobSourceException
        || throwable instanceof WebClientRequestException
        || throwable instanceof TimeoutException;
  }

  private JobPage parse(String body, SearchProfile profile, PageRequest request) {
    try {
      JsonNode root = objectMapper.readTree(body);
      if (root == null || !root.isArray()) {
        throw new MalformedJobSourceResponseException("Lever response must be a JSON array");
      }
      List<RawSourceJob> jobs = new ArrayList<>();
      for (JsonNode job : root) {
        JsonNode id = job.get("id");
        if (id == null || id.asString().isBlank()) {
          throw new MalformedJobSourceResponseException("Lever job is missing its id");
        }
        if (!matches(job, profile)) {
          continue;
        }
        String rawJson = job.toString();
        jobs.add(
            new RawSourceJob(
                profile.sourceKey() + ":" + id.asString(),
                text(job.get("hostedUrl")),
                rawJson,
                sha256(rawJson)));
      }
      return new JobPage(request.page(), -1, jobs, root.size() == request.pageSize());
    } catch (MalformedJobSourceResponseException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new MalformedJobSourceResponseException("Lever returned malformed JSON", exception);
    }
  }

  private static boolean matches(JsonNode job, SearchProfile profile) {
    String searchable =
        (text(job.get("text")) + " " + text(job.get("descriptionPlain"))).toLowerCase(Locale.ROOT);
    boolean keywordMatch =
        java.util.Arrays.stream(profile.keywords().toLowerCase(Locale.ROOT).split("\\s+"))
            .filter(token -> token.length() > 1)
            .anyMatch(searchable::contains);
    if (!keywordMatch) {
      return false;
    }
    if (profile.location() == null || profile.location().isBlank()) {
      return true;
    }
    String location = text(job.path("categories").get("location"));
    return location != null
        && location.toLowerCase(Locale.ROOT).contains(profile.location().toLowerCase(Locale.ROOT));
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
