package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderQueryPlannerTests {
  private final ProviderQueryPlanner planner = new ProviderQueryPlanner();

  @Test
  void generatesOneBoundedQueryPerOrderedRoleUsingCategoryRelevantSkills() {
    var queries =
        planner.plan(
            List.of(
                new ProviderQueryPlanner.TargetRole(11, "Frontend Engineer", "FRONTEND", 1),
                new ProviderQueryPlanner.TargetRole(12, "Data Scientist", "DATA", 2),
                new ProviderQueryPlanner.TargetRole(13, "Sales Manager", "SALES", 3)),
            List.of(
                new ProviderQueryPlanner.CandidateSkill("React", "FRONTEND"),
                new ProviderQueryPlanner.CandidateSkill("TypeScript", "FRONTEND"),
                new ProviderQueryPlanner.CandidateSkill("Machine Learning", "DATA"),
                new ProviderQueryPlanner.CandidateSkill("CRM", "SALES")),
            "");

    assertThat(queries)
        .extracting(ProviderQueryPlanner.GeneratedQuery::text)
        .containsExactly(
            "Frontend Engineer React TypeScript",
            "Data Scientist Machine Learning",
            "Sales Manager CRM");
    assertThat(queries)
        .extracting(ProviderQueryPlanner.GeneratedQuery::generationVersion)
        .containsOnly(ProviderQueryPlanner.GENERATION_VERSION);
    assertThat(queries)
        .extracting(ProviderQueryPlanner.GeneratedQuery::origin)
        .containsOnly("GENERATED");
  }

  @Test
  void advancedOverrideReplacesProviderTextWithoutChangingRoleIntent() {
    var queries =
        planner.plan(
            List.of(
                new ProviderQueryPlanner.TargetRole(11, "Frontend Engineer", "FRONTEND", 1),
                new ProviderQueryPlanner.TargetRole(12, "Data Scientist", "DATA", 2)),
            List.of(new ProviderQueryPlanner.CandidateSkill("React", "FRONTEND")),
            "  UI   engineer React  ");

    assertThat(queries)
        .singleElement()
        .satisfies(
            query -> {
              assertThat(query.roleId()).isNull();
              assertThat(query.text()).isEqualTo("UI engineer React");
              assertThat(query.origin()).isEqualTo("OVERRIDE");
            });
  }
}
