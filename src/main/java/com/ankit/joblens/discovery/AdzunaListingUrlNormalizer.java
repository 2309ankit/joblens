package com.ankit.joblens.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

public final class AdzunaListingUrlNormalizer {
  private static final Map<String, String> MARKET_HOSTS =
      Map.ofEntries(
          Map.entry("at", "www.adzuna.at"),
          Map.entry("au", "www.adzuna.com.au"),
          Map.entry("be", "www.adzuna.be"),
          Map.entry("br", "www.adzuna.com.br"),
          Map.entry("ca", "www.adzuna.ca"),
          Map.entry("ch", "www.adzuna.ch"),
          Map.entry("de", "www.adzuna.de"),
          Map.entry("es", "www.adzuna.es"),
          Map.entry("fr", "www.adzuna.fr"),
          Map.entry("gb", "www.adzuna.co.uk"),
          Map.entry("in", "www.adzuna.in"),
          Map.entry("it", "www.adzuna.it"),
          Map.entry("mx", "www.adzuna.com.mx"),
          Map.entry("nl", "www.adzuna.nl"),
          Map.entry("nz", "www.adzuna.co.nz"),
          Map.entry("pl", "www.adzuna.pl"),
          Map.entry("ru", "www.adzuna.ru"),
          Map.entry("sg", "www.adzuna.sg"),
          Map.entry("us", "www.adzuna.com"),
          Map.entry("za", "www.adzuna.co.za"));

  private AdzunaListingUrlNormalizer() {}

  public static String normalize(String market, String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    try {
      URI uri = URI.create(value.trim());
      String expectedHost = market == null ? null : MARKET_HOSTS.get(market.toLowerCase());
      String actualHost = uri.getHost();
      if (expectedHost == null
          || actualHost == null
          || !MARKET_HOSTS.containsValue(actualHost.toLowerCase(Locale.ROOT))) {
        return value;
      }
      return new URI("https", null, expectedHost, -1, uri.getPath(), null, null).toString();
    } catch (IllegalArgumentException | URISyntaxException exception) {
      return value;
    }
  }
}
