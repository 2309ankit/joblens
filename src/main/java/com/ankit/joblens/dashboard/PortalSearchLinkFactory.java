package com.ankit.joblens.dashboard;

import com.ankit.joblens.onboarding.SearchPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PortalSearchLinkFactory {
  private final PortalSearchQueryPlanner planner;

  public PortalSearchLinkFactory(PortalSearchQueryPlanner planner) {
    this.planner = planner;
  }

  public List<PortalSearchLink> create(
      SearchPreferences preferences, List<String> candidateSkills) {
    List<PortalSearchQuery> queries = planner.plan(preferences, candidateSkills);
    var links = new ArrayList<PortalSearchLink>();
    queries.forEach(
        query ->
            links.add(
                new PortalSearchLink(
                    "LinkedIn",
                    preferences.searchLocation(),
                    query.intent(),
                    query.linkedInQuery(),
                    linkedInUrl(query.linkedInQuery(), preferences.searchLocation()))));
    addNaturalLinks(links, queries, "JobStreet", "Singapore", "https://sg.jobstreet.com");
    addNaturalLinks(links, queries, "SEEK", "Australia", "https://www.seek.com.au");
    addNaturalLinks(links, queries, "SEEK", "New Zealand", "https://www.seek.co.nz");
    return List.copyOf(links);
  }

  private static void addNaturalLinks(
      List<PortalSearchLink> links,
      List<PortalSearchQuery> queries,
      String portal,
      String region,
      String baseUrl) {
    queries.forEach(
        query ->
            links.add(
                new PortalSearchLink(
                    portal,
                    region,
                    query.intent(),
                    query.naturalQuery(),
                    keywordUrl(baseUrl, slug(query.naturalQuery())))));
  }

  private static String linkedInUrl(String query, String location) {
    return UriComponentsBuilder.fromUriString("https://www.linkedin.com/jobs/search/")
        .queryParam("keywords", query)
        .queryParam("location", location)
        .encode()
        .toUriString();
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
