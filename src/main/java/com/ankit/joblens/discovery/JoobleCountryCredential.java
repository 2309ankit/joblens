package com.ankit.joblens.discovery;

public record JoobleCountryCredential(String countryCode, String baseUrl, String apiKey) {
  public JoobleCountryCredential {
    if (countryCode == null || !countryCode.matches("(?i)[a-z]{2}")) {
      throw new IllegalArgumentException("Jooble country-code must contain two letters");
    }
  }

  public boolean hasCredentials() {
    return apiKey != null && !apiKey.isBlank();
  }
}
