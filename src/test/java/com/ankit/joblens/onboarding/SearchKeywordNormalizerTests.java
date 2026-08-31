package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchKeywordNormalizerTests {
  @Test
  void removesEscoIctQualifierWithoutChangingTheStoredRole() {
    assertThat(SearchKeywordNormalizer.normalize("ICT account manager"))
        .isEqualTo("account manager");
    assertThat(
            SearchKeywordNormalizer.normalize(
                "information and communication technology sales manager"))
        .isEqualTo("sales manager");
  }

  @Test
  void preservesMeaningfulNonIctOccupations() {
    assertThat(SearchKeywordNormalizer.normalize("Industrial account manager"))
        .isEqualTo("Industrial account manager");
    assertThat(SearchKeywordNormalizer.normalize("Sales Executive")).isEqualTo("Sales Executive");
  }
}
