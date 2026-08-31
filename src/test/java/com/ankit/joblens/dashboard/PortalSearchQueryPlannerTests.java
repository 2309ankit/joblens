package com.ankit.joblens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.onboarding.SearchPreferences;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortalSearchQueryPlannerTests {
  private final PortalSearchQueryPlanner planner = new PortalSearchQueryPlanner();

  @Test
  void createsNicheRoleSectorTechnologyAndFallbackQueries() {
    var queries =
        planner.plan(
            preferences(), List.of("Java", "Spring", "Spring Boot", "Kafka", "PostgreSQL"));

    assertThat(queries)
        .containsExactly(
            new PortalSearchQuery(
                "Primary role + sectors",
                "\"Senior Java Developer\" AND (\"banking\" OR \"payments\")",
                "Senior Java Developer banking payments"),
            new PortalSearchQuery(
                "Alternate role + technology + sector",
                "(\"Senior Java Developer\" OR \"Senior Backend Engineer\") AND (\"Spring Boot\" OR \"Java\") AND (\"banking\" OR \"payments\")",
                "Senior Backend Engineer Spring Boot Java banking"),
            new PortalSearchQuery(
                "Broad fallback",
                "\"Senior Java Developer\" OR \"Senior Backend Engineer\"",
                "Java Spring Boot"));
  }

  @Test
  void fallsBackToSearchKeywordsWhenLatestDraftHasNoRolesSkillsOrSectors() {
    var preferences =
        new SearchPreferences(
            "", "", "Singapore", "Sales Executive", "SG | Singapore", 2, "ANY", "HYBRID");

    assertThat(planner.plan(preferences, List.of()))
        .containsExactly(
            new PortalSearchQuery(
                "Primary role + sectors", "\"Sales Executive\"", "Sales Executive"),
            new PortalSearchQuery(
                "Alternate role + technology + sector",
                "(\"Sales Executive\") AND (\"Sales Executive\")",
                "Sales Executive Sales Executive"),
            new PortalSearchQuery("Broad fallback", "\"Sales Executive\"", "Sales Executive"));
  }

  private static SearchPreferences preferences() {
    return new SearchPreferences(
        "Senior Java Developer, Senior Backend Engineer",
        "banking, payments",
        "Singapore",
        "Java Spring Boot",
        "SG | Singapore",
        2,
        "PERMANENT",
        "HYBRID");
  }
}
