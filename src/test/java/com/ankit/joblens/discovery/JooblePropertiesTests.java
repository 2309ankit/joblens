package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class JooblePropertiesTests {
  @Test
  void hasNoCredentialsWhenNoCountryIsConfigured() {
    var properties = properties(List.of());

    assertThat(properties.hasCredentials()).isFalse();
    assertThat(properties.configuredCountryCodes()).isEmpty();
    assertThat(properties.supportsCountry("sg")).isFalse();
  }

  @Test
  void supportsMultipleIndependentlyConfiguredCountries() {
    var properties =
        properties(
            List.of(
                new JoobleCountryCredential("sg", "https://sg.jooble.org", "sg-key"),
                new JoobleCountryCredential("my", "https://my.jooble.org", "my-key")));

    assertThat(properties.hasCredentials()).isTrue();
    assertThat(properties.configuredCountryCodes()).containsExactlyInAnyOrder("SG", "MY");
    assertThat(properties.supportsCountry("sg")).isTrue();
    assertThat(properties.supportsCountry("MY")).isTrue();
    assertThat(properties.supportsCountry("in")).isFalse();
    assertThat(properties.credentialFor("my"))
        .isPresent()
        .get()
        .extracting("apiKey")
        .isEqualTo("my-key");
  }

  @Test
  void treatsACountryWithABlankApiKeyAsNotConfigured() {
    var properties =
        properties(List.of(new JoobleCountryCredential("in", "https://in.jooble.org", "")));

    assertThat(properties.hasCredentials()).isFalse();
    assertThat(properties.configuredCountryCodes()).isEmpty();
    assertThat(properties.supportsCountry("in")).isFalse();
    assertThat(properties.credentialFor("in")).isEmpty();
  }

  @Test
  void rejectsDuplicateCountryCodesRegardlessOfCase() {
    assertThatThrownBy(
            () ->
                properties(
                    List.of(
                        new JoobleCountryCredential("sg", "https://sg.jooble.org", "a"),
                        new JoobleCountryCredential("SG", "https://sg.jooble.org", "b"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not repeat");
  }

  @Test
  void rejectsACountryCodeThatIsNotTwoLetters() {
    assertThatThrownBy(() -> new JoobleCountryCredential("sgp", "https://sg.jooble.org", "key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("two letters");
  }

  private JoobleProperties properties(List<JoobleCountryCredential> countries) {
    return new JoobleProperties(countries, Duration.ofSeconds(2), 20, 2, Duration.ofMillis(1));
  }
}
