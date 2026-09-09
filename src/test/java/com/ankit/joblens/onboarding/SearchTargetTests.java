package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchTargetTests {
  @Test
  void parsesAndNormalizesOneMarketPerLine() {
    assertThat(SearchTarget.parse("sg | Singapore\nAU| Sydney\nIN|"))
        .containsExactly(
            new SearchTarget("SG", "Singapore"),
            new SearchTarget("AU", "Sydney"),
            new SearchTarget("IN", ""));
  }

  @Test
  void rejectsAmbiguousOrInvalidMarketInput() {
    assertThatThrownBy(() -> SearchTarget.parse("SG, AU"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SG | Singapore");
    assertThatThrownBy(() -> SearchTarget.parse("Singapore | Singapore"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("two-letter country code");
    assertThatThrownBy(() -> SearchTarget.parse("SG | Singapore\nsg | singapore"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate search markets");
  }
}
