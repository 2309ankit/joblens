package com.ankit.joblens.dashboard;

import com.ankit.joblens.onboarding.SearchPreferences;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PortalSearchLinkFactory {

  public List<PortalSearchLink> create(SearchPreferences preferences) {
    String keywordSlug = slug(preferences.keywords());
    return List.of(
        new PortalSearchLink(
            "LinkedIn",
            preferences.searchLocation(),
            UriComponentsBuilder.fromUriString("https://www.linkedin.com/jobs/search/")
                .queryParam("keywords", preferences.keywords())
                .queryParam("location", preferences.searchLocation())
                .encode()
                .toUriString()),
        new PortalSearchLink(
            "JobStreet", "Singapore", keywordUrl("https://sg.jobstreet.com", keywordSlug)),
        new PortalSearchLink(
            "SEEK", "Australia", keywordUrl("https://www.seek.com.au", keywordSlug)),
        new PortalSearchLink(
            "SEEK", "New Zealand", keywordUrl("https://www.seek.co.nz", keywordSlug)));
  }

  private static String keywordUrl(String baseUrl, String keywordSlug) {
    return UriComponentsBuilder.fromUriString(baseUrl)
        .pathSegment(keywordSlug + "-jobs")
        .build()
        .toUriString();
  }

  private static String slug(String value) {
    String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    return slug.replaceAll("(^-)|(-$)", "");
  }
}
