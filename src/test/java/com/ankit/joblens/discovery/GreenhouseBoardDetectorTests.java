package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GreenhouseBoardDetectorTests {
  private final GreenhouseBoardDetector detector = new GreenhouseBoardDetector();

  @Test
  void detectsCurrentAndLegacyOfficialBoardUrls() {
    assertThat(detector.detect("https://job-boards.greenhouse.io/ExampleCo/jobs/123"))
        .contains(
            new DetectedSourceBoard(
                JobSource.GREENHOUSE, "exampleco", "https://job-boards.greenhouse.io/exampleco"));
    assertThat(detector.detect("https://boards.greenhouse.io/embed/job_app?for=ExampleCo"))
        .contains(
            new DetectedSourceBoard(
                JobSource.GREENHOUSE, "exampleco", "https://job-boards.greenhouse.io/exampleco"));
  }

  @Test
  void rejectsTrackingLookalikeAndMalformedUrls() {
    assertThat(
            detector.detect(
                "https://www.adzuna.sg/details/1?next=https://job-boards.greenhouse.io/example"))
        .isEmpty();
    assertThat(detector.detect("https://job-boards.greenhouse.io/bad.token/jobs/1")).isEmpty();
    assertThat(detector.detect("not a url")).isEmpty();
  }
}
