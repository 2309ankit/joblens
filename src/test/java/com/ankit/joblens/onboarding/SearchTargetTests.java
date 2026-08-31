package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchTargetTests {
  @Test
  void parsesNormalizesAndDeduplicatesOneMarketPerLine() {
    assertThat(SearchTarget.parse("sg | Singapore\nAU| Sydney\nSG | singapore"))
        .containsExactly(new SearchTarget("SG", "Singapore"), new SearchTarget("AU", "Sydney"));
  }

  @Test
  void rejectsAmbiguousOrInvalidMarketInput() {
    assertThatThrownBy(() -> SearchTarget.parse("SG, AU"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SG | Singapore");
    assertThatThrownBy(() -> SearchTarget.parse("Singapore | Singapore"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("two-letter country code");
  }
}
