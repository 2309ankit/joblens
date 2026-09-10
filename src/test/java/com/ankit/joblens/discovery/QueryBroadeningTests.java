package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueryBroadeningTests {

  @Test
  void dropsUpToTwoTrailingTermsForALongerQuery() {
    assertThat(QueryBroadening.broaden("Backend Engineer Apache Camel IBM MQ"))
        .containsExactly("Backend Engineer Apache Camel IBM", "Backend Engineer Apache Camel");
  }

  @Test
  void dropsDownToASingleRemainingTermForAThreeTermQuery() {
    assertThat(QueryBroadening.broaden("Software Engineer Node.js"))
        .containsExactly("Software Engineer", "Software");
  }

  @Test
  void returnsNoVariantsForASingleTerm() {
    assertThat(QueryBroadening.broaden("Engineer")).isEmpty();
  }

  @Test
  void collapsesRepeatedWhitespaceBetweenTerms() {
    assertThat(QueryBroadening.broaden("Role   Skill1  Skill2"))
        .containsExactly("Role Skill1", "Role");
  }
}
