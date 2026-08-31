package com.ankit.joblens.discovery;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

public class AdzunaJobSourceClient implements JobSourceClient {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final AdzunaProperties properties;

  public AdzunaJobSourceClient(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, AdzunaProperties properties) {
    this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.ADZUNA;
  }

  @Override
  public int pageSize() {
    return properties.pageSize();
  }

  @Override
  public JobPage search(SearchProfile profile, PageRequest request) {
    if (!properties.hasCredentials()) {
      throw new MissingJobSourceCredentialsException(
          "Adzuna credentials are missing; set ADZUNA_APP_ID and ADZUNA_APP_KEY");
    }
    if (profile.sourceKey() == null || profile.sourceKey().isBlank()) {
      throw new JobSourceException(
          "Adzuna profile " + profile.profileId() + " has no country source_key");
    }

    Mono<String> call =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .pathSegment(
                            "jobs",
                            profile.sourceKey().toLowerCase(),
                            "search",
                            Integer.toString(request.page()))
                        .queryParam("app_id", properties.appId())
                        .queryParam("app_key", properties.appKey())
                        .queryParam("results_per_page", request.pageSize())
                        .queryParam("what", profile.keywords())
                        .queryParamIfPresent(
                            "where", java.util.Optional.ofNullable(profile.location()))
                        .queryParam("content-type", "application/json")
                        .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchangeToMono(
                response ->
                    handleResponse(response.statusCode(), response.bodyToMono(String.class)));

    call = call.timeout(properties.timeout());
    if (properties.retryAttempts() > 1) {
      call =
          call.retryWhen(
              Retry.backoff(properties.retryAttempts() - 1, properties.retryBackoff())
                  .filter(this::isTransient)
                  .jitter(0));
    }

    String body;
    try {
      body = call.block();
    } catch (JobSourceException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new JobSourceException(
          "Adzuna request failed for profile " + profile.profileId() + " page " + request.page(),
          exception);
    }
    if (body == null) {
      throw new JobSourceException(
          "Adzuna returned no response body for profile " + profile.profileId());
    }
    return parse(body, request, profile.sourceKey());
  }

  private Mono<String> handleResponse(HttpStatusCode statusCode, Mono<String> body) {
    if (statusCode.is2xxSuccessful()) {
      return body;
    }
    if (statusCode.value() == 429 || statusCode.value() == 500 || statusCode.value() == 503) {
      return body.defaultIfEmpty("")
          .flatMap(
              ignored ->
                  Mono.error(
                      new TransientJobSourceException(
                          "Transient Adzuna HTTP status " + statusCode.value())));
    }
    return body.defaultIfEmpty("")
        .flatMap(
            ignored ->
                Mono.error(
                    new JobSourceException(
                        "Non-retryable Adzuna HTTP status " + statusCode.value())));
  }

  private boolean isTransient(Throwable throwable) {
    return throwable instanceof TransientJobSourceException
        || throwable instanceof WebClientRequestException
        || throwable instanceof TimeoutException;
  }

  private JobPage parse(String body, PageRequest request, String market) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode results = root.get("results");
      if (results == null || !results.isArray()) {
        throw new MalformedJobSourceResponseException(
            "Adzuna response is missing the results array");
      }
      List<RawSourceJob> jobs = new ArrayList<>();
      for (JsonNode job : results) {
        JsonNode id = job.get("id");
        if (id == null || id.asString().isBlank()) {
          throw new MalformedJobSourceResponseException("Adzuna job is missing its id");
        }
        String rawJson = job.toString();
        JsonNode redirectUrl = job.get("redirect_url");
        jobs.add(
            new RawSourceJob(
                id.asString(),
                redirectUrl == null || redirectUrl.isNull()
                    ? null
                    : AdzunaListingUrlNormalizer.normalize(market, redirectUrl.asString()),
                rawJson,
                sha256(rawJson)));
      }
      JsonNode countNode = root.get("count");
      long count = countNode == null || !countNode.isNumber() ? -1 : countNode.asLong();
      boolean hasMore =
          !jobs.isEmpty()
              && (count >= 0
                  ? (long) request.page() * request.pageSize() < count
                  : jobs.size() == request.pageSize());
      return new JobPage(request.page(), count, jobs, hasMore);
    } catch (MalformedJobSourceResponseException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new MalformedJobSourceResponseException("Adzuna returned malformed JSON", exception);
    }
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
