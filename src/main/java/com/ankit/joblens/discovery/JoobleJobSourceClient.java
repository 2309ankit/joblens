package com.ankit.joblens.discovery;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JoobleJobSourceClient implements JobSourceClient {
  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final JoobleProperties properties;

  public JoobleJobSourceClient(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, JoobleProperties properties) {
    this.webClient = webClientBuilder.build();
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.JOOBLE;
  }

  @Override
  public int pageSize() {
    return properties.pageSize();
  }

  @Override
  public boolean supportsQueryBroadening() {
    return true;
  }

  @Override
  public JobPage search(SearchProfile profile, PageRequest request) {
    String countryCode = profile.sourceKey() == null ? "" : profile.sourceKey().trim();
    JoobleCountryCredential credential =
        properties
            .credentialFor(countryCode)
            .orElseThrow(
                () ->
                    new MissingJobSourceCredentialsException(
                        "Jooble credentials are missing for country "
                            + countryCode.toUpperCase(Locale.ROOT)
                            + "; set JOOBLE_"
                            + countryCode.toUpperCase(Locale.ROOT)
                            + "_API_KEY"));
    Mono<String> call =
        webClient
            .post()
            .uri(
                UriComponentsBuilder.fromUriString(credential.baseUrl())
                    .pathSegment("api", credential.apiKey())
                    .build()
                    .toUri())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(
                Map.of(
                    "keywords", profile.keywords(),
                    "location", effectiveLocation(profile),
                    "page", request.page(),
                    "ResultOnPage", request.pageSize()))
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
        throw new JobSourceException("Jooble returned no response body for " + profile.profileId());
      }
      return parse(body, request);
    } catch (JobSourceException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new JobSourceException(
          "Jooble request failed for profile " + profile.profileId(), exception);
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
                          "Transient Jooble HTTP status " + statusCode.value())));
    }
    return body.defaultIfEmpty("")
        .flatMap(
            ignored ->
                Mono.error(
                    new JobSourceException(
                        "Non-retryable Jooble HTTP status " + statusCode.value())));
  }

  private boolean isTransient(Throwable throwable) {
    return throwable instanceof TransientJobSourceException
        || throwable instanceof WebClientRequestException
        || throwable instanceof TimeoutException;
  }

  private JobPage parse(String body, PageRequest request) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode jobsNode = root == null ? null : root.get("jobs");
      if (jobsNode == null || !jobsNode.isArray()) {
        throw new MalformedJobSourceResponseException("Jooble response is missing the jobs array");
      }
      List<RawSourceJob> jobs = new ArrayList<>();
      for (JsonNode job : jobsNode) {
        JsonNode id = job.get("id");
        if (id == null || id.asString().isBlank()) {
          throw new MalformedJobSourceResponseException("Jooble job is missing its id");
        }
        String rawJson = job.toString();
        jobs.add(new RawSourceJob(id.asString(), text(job.get("link")), rawJson, sha256(rawJson)));
      }
      long totalCount = root.path("totalCount").isNumber() ? root.path("totalCount").asLong() : -1;
      boolean hasMore =
          !jobs.isEmpty()
              && (totalCount >= 0
                  ? (long) request.page() * request.pageSize() < totalCount
                  : jobs.size() == request.pageSize());
      return new JobPage(request.page(), totalCount, jobs, hasMore);
    } catch (MalformedJobSourceResponseException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new MalformedJobSourceResponseException("Jooble returned malformed JSON", exception);
    }
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.asString();
  }

  private static String effectiveLocation(SearchProfile profile) {
    if (profile.location() != null && !profile.location().isBlank()) {
      return profile.location();
    }
    String sourceKey = profile.sourceKey() == null ? "" : profile.sourceKey().trim();
    if (!sourceKey.matches("(?i)[a-z]{2}")) {
      throw new JobSourceException("Jooble country-wide search needs a country source key");
    }
    return Locale.of("", sourceKey.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH);
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
