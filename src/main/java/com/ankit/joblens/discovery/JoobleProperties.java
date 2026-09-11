package com.ankit.joblens.discovery;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("joblens.jooble")
public record JoobleProperties(
    List<JoobleCountryCredential> countries,
    Duration timeout,
    int pageSize,
    int retryAttempts,
    Duration retryBackoff) {

  public JoobleProperties {
    countries = countries == null ? List.of() : List.copyOf(countries);
    long distinctCodes =
        countries.stream().map(c -> c.countryCode().toUpperCase(Locale.ROOT)).distinct().count();
    if (distinctCodes != countries.size()) {
      throw new IllegalArgumentException("Jooble country codes must not repeat");
    }
    if (pageSize < 1 || retryAttempts < 1) {
      throw new IllegalArgumentException("Jooble page-size and retry-attempts must be positive");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Jooble timeout must be positive");
    }
    if (retryBackoff == null || retryBackoff.isNegative()) {
      throw new IllegalArgumentException("Jooble retry-backoff must not be negative");
    }
  }

  public boolean hasCredentials() {
    return countries.stream().anyMatch(JoobleCountryCredential::hasCredentials);
  }

  public boolean supportsCountry(String requestedCountryCode) {
    return credentialFor(requestedCountryCode).isPresent();
  }

  public Optional<JoobleCountryCredential> credentialFor(String requestedCountryCode) {
    if (requestedCountryCode == null) {
      return Optional.empty();
    }
    return countries.stream()
        .filter(c -> c.countryCode().equalsIgnoreCase(requestedCountryCode))
        .filter(JoobleCountryCredential::hasCredentials)
        .findFirst();
  }

  public List<String> configuredCountryCodes() {
    return countries.stream()
        .filter(JoobleCountryCredential::hasCredentials)
        .map(c -> c.countryCode().toUpperCase(Locale.ROOT))
        .toList();
  }
}
