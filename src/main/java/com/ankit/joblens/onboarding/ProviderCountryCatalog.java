package com.ankit.joblens.onboarding;

import com.ankit.joblens.discovery.JoobleProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProviderCountryCatalog {
  private static final Set<String> ADZUNA_COUNTRIES =
      Set.of(
          "AT", "AU", "BE", "BR", "CA", "CH", "DE", "ES", "FR", "GB", "IN", "IT", "MX", "NL", "NZ",
          "PL", "RU", "SG", "US", "ZA");

  private final JoobleProperties joobleProperties;

  public ProviderCountryCatalog(JoobleProperties joobleProperties) {
    this.joobleProperties = joobleProperties;
  }

  public List<IntegratedCountry> countries() {
    Set<String> codes = new LinkedHashSet<>(ADZUNA_COUNTRIES);
    if (joobleProperties.hasCredentials()) {
      codes.add(joobleProperties.countryCode().toUpperCase(Locale.ROOT));
    }
    var result = new ArrayList<IntegratedCountry>();
    for (String code : codes) {
      List<String> sources = sources(code);
      result.add(
          new IntegratedCountry(
              code,
              countryName(code),
              sources,
              "Integrated search available through " + String.join(" and ", sources) + "."));
    }
    result.sort(Comparator.comparing(IntegratedCountry::name));
    return List.copyOf(result);
  }

  public void requireSupported(String countryCode) {
    String normalized = countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
    if (sources(normalized).isEmpty()) {
      throw new IllegalArgumentException(
          "No configured job source supports the selected country: " + normalized);
    }
  }

  private List<String> sources(String code) {
    var sources = new ArrayList<String>();
    if (ADZUNA_COUNTRIES.contains(code)) {
      sources.add("ADZUNA");
    }
    if (joobleProperties.hasCredentials() && joobleProperties.supportsCountry(code)) {
      sources.add("JOOBLE");
    }
    return List.copyOf(sources);
  }

  private static String countryName(String code) {
    return Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
  }
}
