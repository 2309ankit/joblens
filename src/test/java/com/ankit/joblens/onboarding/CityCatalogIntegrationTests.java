package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class CityCatalogIntegrationTests {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("joblens_city_catalog_test")
          .withUsername("joblens")
          .withPassword("joblens-test");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private ProfileIntelligenceRepository repository;

  @Test
  void findsAWellKnownCityByPrefixWithinItsCountry() {
    assertThat(repository.cityOptions("MY", "kuala"))
        .extracting(CityOption::name)
        .contains("Kuala Lumpur");
  }

  @Test
  void doesNotLeakCitiesFromOtherCountries() {
    assertThat(repository.cityOptions("SG", "kuala")).isEmpty();
  }

  @Test
  void limitsResultsAndOrdersByPopulationDescending() {
    var results = repository.cityOptions("IN", "");

    assertThat(results).hasSizeLessThanOrEqualTo(10);
    assertThat(results).isNotEmpty();
  }

  @Test
  void rejectsAMalformedCountryCode() {
    assertThatThrownBy(() -> repository.cityOptions("malaysia", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("two-letter country code");
  }
}
