package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeverBoardDetectorTests {
  private final LeverBoardDetector detector = new LeverBoardDetector();

  @Test
  void detectsOnlyOfficialGlobalLeverHostedJobUrls() {
    assertThat(detector.detect("https://jobs.lever.co/Example-Co/abc-123"))
        .contains(
            new DetectedSourceBoard(
                JobSource.LEVER, "example-co", "https://jobs.lever.co/example-co"));

    assertThat(detector.detect("http://jobs.lever.co/example/1")).isEmpty();
    assertThat(detector.detect("https://jobs.eu.lever.co/example/1")).isEmpty();
    assertThat(detector.detect("https://jobs.lever.co.evil.example/example/1")).isEmpty();
    assertThat(detector.detect("https://tracking.example/?next=https://jobs.lever.co/example/1"))
        .isEmpty();
  }
}
