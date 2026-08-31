package com.ankit.joblens.onboarding;

import java.util.List;

public record EscoTaxonomyPage(int page, int pageSize, int total, List<Concept> concepts) {
  public boolean hasMore() {
    return (long) (page + 1) * pageSize < total;
  }

  public record Concept(
      String uri, String preferredLabel, List<String> alternativeLabels, String description) {}
}
