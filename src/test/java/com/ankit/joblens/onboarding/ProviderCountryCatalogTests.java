package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ankit.joblens.discovery.JoobleCountryCredential;
import com.ankit.joblens.discovery.JoobleProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderCountryCatalogTests {
  @Test
  void exposesCountryNamesAndConfiguredProviderCapabilities() {
    var catalog =
        new ProviderCountryCatalog(
            new JoobleProperties(
                List.of(new JoobleCountryCredential("sg", "https://sg.jooble.org", "regional-key")),
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

  @Test
  void addsASecondJoobleCountryWithNoAdzunaCoverageUsingJoobleOnly() {
    var catalog =
        new ProviderCountryCatalog(
            new JoobleProperties(
                List.of(
                    new JoobleCountryCredential("sg", "https://sg.jooble.org", "sg-key"),
                    new JoobleCountryCredential("my", "https://my.jooble.org", "my-key")),
                Duration.ofSeconds(2),
                20,
                1,
                Duration.ZERO));

    assertThat(catalog.countries())
        .filteredOn(country -> country.code().equals("MY"))
        .singleElement()
        .satisfies(
            country -> {
              assertThat(country.name()).isEqualTo("Malaysia");
              assertThat(country.sources()).containsExactly("JOOBLE");
            });
  }

  @Test
  void ignoresAConfiguredCountryThatHasNoApiKeySet() {
    var catalog =
        new ProviderCountryCatalog(
            new JoobleProperties(
                List.of(new JoobleCountryCredential("my", "https://my.jooble.org", "")),
                Duration.ofSeconds(2),
                20,
                1,
                Duration.ZERO));

    assertThat(catalog.countries()).noneMatch(country -> country.code().equals("MY"));
  }
}
