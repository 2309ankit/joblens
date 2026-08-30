package com.ankit.joblens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.onboarding.SearchPreferences;
import org.junit.jupiter.api.Test;

class PortalSearchLinkFactoryTests {
  private final PortalSearchLinkFactory factory = new PortalSearchLinkFactory();

  @Test
  void createsOfficialPortalSearchLinksFromPreferences() {
    var links = factory.create(preferences("Senior Java & Spring", "Singapore"));

    assertThat(links)
        .extracting(PortalSearchLink::portal)
        .containsExactly("LinkedIn", "JobStreet", "SEEK", "SEEK");
    assertThat(links.get(0).url())
        .isEqualTo(
            "https://www.linkedin.com/jobs/search/?keywords=Senior%20Java%20%26%20Spring&location=Singapore");
    assertThat(links.get(1).url()).isEqualTo("https://sg.jobstreet.com/senior-java-spring-jobs");
    assertThat(links.get(2).url()).isEqualTo("https://www.seek.com.au/senior-java-spring-jobs");
    assertThat(links.get(3).url()).isEqualTo("https://www.seek.co.nz/senior-java-spring-jobs");
  }

  private static SearchPreferences preferences(String keywords, String location) {
    return new SearchPreferences(
        "Java Developer", "banking", location, keywords, location, "sg", 2, "PERMANENT", "HYBRID");
  }
}
