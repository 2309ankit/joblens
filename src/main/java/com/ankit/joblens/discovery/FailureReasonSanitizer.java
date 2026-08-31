package com.ankit.joblens.discovery;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class FailureReasonSanitizer {
  private static final int MAX_LENGTH = 1000;
  private static final Pattern SECRET_VALUE =
      Pattern.compile(
          "(?i)(app[_-]?key|api[_-]?key|access[_-]?token|token|password)(\\s*[=:]\\s*)([^\\s&,;]+)");
  private static final Pattern JOOBLE_KEY_PATH = Pattern.compile("(?i)(/api/)([^/?#\\s]+)");

  String sanitize(Throwable failure) {
    String message = failure.getMessage() == null ? "No failure message" : failure.getMessage();
    return sanitize(failure.getClass().getSimpleName() + ": " + message);
  }

  String sanitize(String reason) {
    if (reason == null) {
      return null;
    }
    String sanitized = SECRET_VALUE.matcher(reason).replaceAll("$1$2[REDACTED]");
    sanitized = JOOBLE_KEY_PATH.matcher(sanitized).replaceAll("$1[REDACTED]");
    return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
  }
}
