package com.ankit.joblens.discovery;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GreenhouseBoardDetector {
  private static final String CURRENT_HOST = "job-boards.greenhouse.io";
  private static final String LEGACY_HOST = "boards.greenhouse.io";

  public Optional<DetectedBoard> detect(String sourceUrl) {
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return Optional.empty();
    }
    try {
      URI uri = URI.create(sourceUrl.trim());
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        return Optional.empty();
      }
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      if (!CURRENT_HOST.equals(host) && !LEGACY_HOST.equals(host)) {
        return Optional.empty();
      }
      String sourceKey = firstPathSegment(uri.getPath()).orElseGet(() -> queryToken(uri));
      if (!sourceKey.matches("[a-z0-9_-]+")) {
        return Optional.empty();
      }
      return Optional.of(new DetectedBoard(sourceKey, "https://" + CURRENT_HOST + "/" + sourceKey));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static Optional<String> firstPathSegment(String path) {
    if (path == null) {
      return Optional.empty();
    }
    Optional<String> first =
        Arrays.stream(path.split("/"))
            .map(String::trim)
            .filter(segment -> !segment.isBlank())
            .map(segment -> segment.toLowerCase(Locale.ROOT))
            .findFirst();
    return first.filter(segment -> !segment.equals("embed"));
  }

  private static String queryToken(URI uri) {
    if (uri.getRawQuery() == null) {
      return "";
    }
    return Arrays.stream(uri.getRawQuery().split("&"))
        .map(parameter -> parameter.split("=", 2))
        .filter(parts -> parts.length == 2 && parts[0].equals("for"))
        .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
        .map(value -> value.toLowerCase(Locale.ROOT))
        .findFirst()
        .orElse("");
  }

  public record DetectedBoard(String sourceKey, String canonicalUrl) {}
}
