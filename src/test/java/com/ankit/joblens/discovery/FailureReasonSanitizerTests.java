package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureReasonSanitizerTests {
  private final FailureReasonSanitizer sanitizer = new FailureReasonSanitizer();

  @Test
  void removesNamedSecretsAndJoobleKeyPaths() {
    String sanitized =
        sanitizer.sanitize(
            "request https://jooble.example/api/super-secret?x=1 failed; api_key=also-secret token: third-secret");

    assertThat(sanitized)
        .contains("/api/[REDACTED]", "api_key=[REDACTED]", "token: [REDACTED]")
        .doesNotContain("super-secret", "also-secret", "third-secret");
  }
}
