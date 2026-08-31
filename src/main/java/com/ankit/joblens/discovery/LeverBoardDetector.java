package com.ankit.joblens.discovery;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LeverBoardDetector implements SourceBoardDetector {
  private static final String HOST = "jobs.lever.co";

  @Override
  public Optional<DetectedSourceBoard> detect(String sourceUrl) {
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return Optional.empty();
    }
    try {
      URI uri = URI.create(sourceUrl.trim());
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || !HOST.equals(uri.getHost().toLowerCase(Locale.ROOT))) {
        return Optional.empty();
      }
      Optional<String> site =
          Arrays.stream(uri.getPath().split("/"))
              .map(String::trim)
              .filter(segment -> !segment.isBlank())
              .map(segment -> segment.toLowerCase(Locale.ROOT))
              .findFirst()
              .filter(segment -> segment.matches("[a-z0-9_-]+"));
      return site.map(
          sourceKey ->
              new DetectedSourceBoard(
                  JobSource.LEVER, sourceKey, "https://" + HOST + "/" + sourceKey));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
