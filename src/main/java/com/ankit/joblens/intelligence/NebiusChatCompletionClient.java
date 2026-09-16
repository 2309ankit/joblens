package com.ankit.joblens.intelligence;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared low-level transport for Nebius Token Factory's OpenAI-compatible chat/completions
 * endpoint. Extracts and validates only the envelope (that {@code choices[0].message.content} is
 * present text, plus token usage); callers own their own request-specific system prompt and
 * response-schema validation.
 */
public class NebiusChatCompletionClient {
  private static final String CONTENT = "content";

  private final WebClient webClient;
  private final ObjectMapper objectMapper;

  public NebiusChatCompletionClient(
      WebClient.Builder builder, ObjectMapper objectMapper, String baseUrl) {
    this.webClient = builder.baseUrl(baseUrl).build();
    this.objectMapper = objectMapper;
  }

  public ChatCompletionResult complete(
      String apiKey,
      String model,
      String systemPrompt,
      String userJson,
      int maxOutputTokens,
      Duration timeout) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put(
        "messages",
        List.of(
            Map.of("role", "system", CONTENT, systemPrompt),
            Map.of("role", "user", CONTENT, userJson)));
    payload.put("temperature", 0);
    payload.put("max_tokens", maxOutputTokens);
    payload.put("response_format", Map.of("type", "json_object"));
    payload.put("store", false);
    // Hybrid-reasoning models (e.g. Nemotron 3 Nano) can spend the whole max_tokens budget on a
    // hidden thinking trace and leave message.content null; every caller of this shared transport
    // is a bounded lookup, not something that benefits from chain-of-thought.
    payload.put("chat_template_kwargs", Map.of("enable_thinking", false));

    String response;
    try {
      response =
          webClient
              .post()
              .uri(uriBuilder -> uriBuilder.pathSegment("chat", "completions").build())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .bodyValue(payload)
              .exchangeToMono(
                  httpResponse -> {
                    if (httpResponse.statusCode().is2xxSuccessful()) {
                      return httpResponse.bodyToMono(String.class);
                    }
                    return httpResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(
                            ignored ->
                                Mono.error(
                                    new NebiusChatCompletionException(
                                        "Nebius HTTP status "
                                            + httpResponse.statusCode().value())));
                  })
              .timeout(timeout)
              .block();
    } catch (NebiusChatCompletionException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new NebiusChatCompletionException("Nebius chat completion request failed", exception);
    }
    if (response == null || response.isBlank()) {
      throw new NebiusChatCompletionException("Nebius returned an empty response");
    }
    return extract(response);
  }

  private ChatCompletionResult extract(String response) {
    JsonNode root;
    try {
      root = objectMapper.readTree(response);
    } catch (JacksonException exception) {
      throw new NebiusChatCompletionException("Nebius returned malformed JSON", exception);
    }
    JsonNode content = root.path("choices").path(0).path("message").path(CONTENT);
    if (!content.isTextual()) {
      throw new NebiusChatCompletionException("choices[0].message.content must be text");
    }
    JsonNode usage = root.path("usage");
    return new ChatCompletionResult(
        content.asText(),
        optionalNonNegativeInteger(usage, "prompt_tokens"),
        optionalNonNegativeInteger(usage, "completion_tokens"));
  }

  private static Integer optionalNonNegativeInteger(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || value.asInt() < 0) {
      return null;
    }
    return value.asInt();
  }
}
