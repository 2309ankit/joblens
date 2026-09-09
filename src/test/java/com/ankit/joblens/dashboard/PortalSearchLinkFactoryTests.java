package com.ankit.joblens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.onboarding.SearchPreferences;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortalSearchLinkFactoryTests {
  private final PortalSearchLinkFactory factory =
      new PortalSearchLinkFactory(new PortalSearchQueryPlanner());

  @Test
  void createsOnlyTheOfficialPortalSearchesForSelectedMarkets() {
    var links = factory.create(preferences(), List.of("Java", "Spring Boot"));

    assertThat(links).hasSize(12);
    assertThat(links).filteredOn(link -> link.portal().equals("LinkedIn")).hasSize(6);
    assertThat(links).filteredOn(link -> link.portal().equals("JobStreet")).hasSize(3);
    assertThat(links)
        .filteredOn(link -> link.portal().equals("SEEK") && link.region().equals("Australia"))
        .hasSize(3);
    assertThat(links).noneMatch(link -> link.region().equals("New Zealand"));
    assertThat(links.getFirst().url())
        .contains("https://www.linkedin.com/jobs/search/?keywords=")
        .contains("location=Singapore")
        .contains("%22Senior%20Java%20Developer%22%20AND");
    assertThat(links.get(3).url())
        .isEqualTo("https://sg.jobstreet.com/senior-java-developer-banking-payments-jobs");
    assertThat(links.get(9).url())
        .isEqualTo("https://www.seek.com.au/senior-java-developer-banking-payments-jobs");
  }

  @Test
  void omitsPortalLinksWhenNoSearchIntentExists() {
    var preferences =
        new SearchPreferences("", "", "Singapore", "", "SG | Singapore", 2, "ANY", "HYBRID");

    assertThat(factory.create(preferences, List.of())).isEmpty();
  }

  @Test
  void usesTheCountryNameForCountryWideOutboundSearches() {
    var preferences =
        new SearchPreferences(
            "Data Analyst", "Technology", "Sydney", "", "AU | ", 2, "ANY", "HYBRID");

    var links = factory.create(preferences, List.of("SQL"));

    assertThat(links.getFirst().region()).isEqualTo("Australia");
    assertThat(links.getFirst().url()).contains("location=Australia");
  }

  private static SearchPreferences preferences() {
    return new SearchPreferences(
        "Senior Java Developer, Senior Backend Engineer",
        "banking, payments",
        "Singapore",
        "Java Spring Boot",
        "SG | Singapore\nAU | Sydney",
        2,
        "PERMANENT",
        "HYBRID");
  }
}
