package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.discovery.JoobleProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderCountryCatalogTests {
  @Test
  void exposesCountryNamesAndConfiguredProviderCapabilities() {
    var catalog =
        new ProviderCountryCatalog(
            new JoobleProperties(
                "regional-key",
                "https://sg.jooble.org",
                "sg",
                Duration.ofSeconds(2),
                20,
                1,
                Duration.ZERO));

    assertThat(catalog.countries())
        .filteredOn(country -> country.code().equals("SG"))
        .singleElement()
        .satisfies(
            country -> {
              assertThat(country.name()).isEqualTo("Singapore");
              assertThat(country.sources()).containsExactly("ADZUNA", "JOOBLE");
              assertThat(country.capabilityExplanation()).contains("ADZUNA", "JOOBLE");
            });
    assertThatThrownBy(() -> catalog.requireSupported("JP"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No configured job source supports");
  }
}
