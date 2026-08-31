package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdzunaListingUrlNormalizerTests {
  @Test
  void forcesTheSelectedMarketAndRemovesTrackingParameters() {
    String canonical =
        AdzunaListingUrlNormalizer.normalize(
            "in", "https://www.adzuna.co.uk/details/123?utm_medium=api&utm_source=test");
    assertThat(canonical).isEqualTo("https://www.adzuna.in/details/123");
    assertThat(AdzunaListingUrlNormalizer.normalize("in", canonical)).isEqualTo(canonical);
    assertThat(
            AdzunaListingUrlNormalizer.normalize(
                "sg", "https://www.adzuna.sg/details/456?utm_medium=api"))
        .isEqualTo("https://www.adzuna.sg/details/456");
  }

  @Test
  void leavesNonAdzunaEmployerLinksUntouched() {
    assertThat(
            AdzunaListingUrlNormalizer.normalize(
                "in", "https://careers.example.com/jobs/account-manager"))
        .isEqualTo("https://careers.example.com/jobs/account-manager");
  }
}
