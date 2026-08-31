package com.ankit.joblens.onboarding;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class EscoTaxonomyClient {
  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final EscoProperties properties;

  public EscoTaxonomyClient(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, EscoProperties properties) {
    WebClient.Builder configured =
        webClientBuilder
            .baseUrl(properties.baseUrl())
            .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(64 * 1024 * 1024));
    if (properties.insecureTls()) {
      try {
        var sslContext =
            SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        configured.clientConnector(
            new ReactorClientHttpConnector(
                HttpClient.create().secure(ssl -> ssl.sslContext(sslContext))));
      } catch (Exception exception) {
        throw new IllegalStateException("Unable to configure insecure ESCO TLS", exception);
      }
    }
    this.webClient = configured.build();
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public EscoTaxonomyPage fetch(String type, int page) {
    if (!List.of("skill", "occupation").contains(type)) {
      throw new IllegalArgumentException("Unsupported ESCO concept type: " + type);
    }
    Mono<String> call =
        webClient
            .get()
            .uri(
                builder ->
                    builder
                        .path("/search")
                        .queryParam("type", type)
                        .queryParam("language", "en")
                        .queryParam("limit", properties.pageSize())
                        .queryParam("offset", page)
                        .queryParam("full", false)
                        .queryParam("viewObsolete", false)
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
        throw new IllegalStateException("ESCO returned no response body");
      }
      return parse(body, page);
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "ESCO request failed for " + type + " page " + page, exception);
    }
  }

  private Mono<String> handleResponse(HttpStatusCode status, Mono<String> body) {
    if (status.is2xxSuccessful()) {
      return body;
    }
    if (status.value() == 429 || status.value() == 500 || status.value() == 503) {
      return body.defaultIfEmpty("")
          .flatMap(
              ignored ->
                  Mono.error(
                      new EscoTransientException("Transient ESCO HTTP status " + status.value())));
    }
    return body.defaultIfEmpty("")
        .flatMap(
            ignored ->
                Mono.error(
                    new IllegalStateException("Non-retryable ESCO HTTP status " + status.value())));
  }

  private boolean isTransient(Throwable failure) {
    return failure instanceof EscoTransientException
        || failure instanceof WebClientRequestException
        || failure instanceof TimeoutException;
  }

  private EscoTaxonomyPage parse(String body, int requestedPage) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode results = root.path("_embedded").path("results");
      if (!results.isArray()) {
        throw new IllegalArgumentException("ESCO response is missing _embedded.results");
      }
      var concepts = new ArrayList<EscoTaxonomyPage.Concept>();
      for (JsonNode result : results) {
        String uri = requiredText(result, "uri");
        String preferredLabel = result.path("preferredLabel").path("en").asString("").trim();
        String label = preferredLabel.isBlank() ? requiredText(result, "title") : preferredLabel;
        List<String> alternatives = new ArrayList<>();
        JsonNode alternativeLabels = result.path("alternativeLabel").path("en");
        if (alternativeLabels.isArray()) {
          alternativeLabels.forEach(
              value -> {
                String alternative = value.asString("").trim();
                if (!alternative.isBlank() && !alternative.equalsIgnoreCase(label)) {
                  alternatives.add(alternative);
                }
              });
        }
        String description =
            result.path("description").path("en").path("literal").asString("").trim();
        concepts.add(new EscoTaxonomyPage.Concept(uri, label, alternatives, description));
      }
      return new EscoTaxonomyPage(
          root.path("offset").asInt(requestedPage),
          root.path("limit").asInt(properties.pageSize()),
          root.path("total").asInt(),
          List.copyOf(concepts));
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("ESCO returned malformed JSON", exception);
    }
  }

  private static String requiredText(JsonNode node, String field) {
    String value = node.path(field).asString("").trim();
    if (value.isBlank()) {
      throw new IllegalArgumentException("ESCO concept is missing " + field);
    }
    return value;
  }

  private static final class EscoTransientException extends RuntimeException {
    private EscoTransientException(String message) {
      super(message);
    }
  }
}
