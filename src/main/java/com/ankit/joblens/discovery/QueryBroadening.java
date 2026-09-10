package com.ankit.joblens.discovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generated provider queries are role name plus up to two skills, joined as plain AND-style
 * keywords (see ProviderQueryPlanner). A provider search on the full phrase can legitimately return
 * zero live postings even when setup is correct. This computes progressively broader variants by
 * dropping trailing terms, narrowest first, so a discovery attempt can retry before accepting a
 * zero-result response.
 */
final class QueryBroadening {

  static final int MAX_ATTEMPTS = 2;

  private QueryBroadening() {}

  static List<String> broaden(String keywords) {
    String[] tokens = keywords.trim().split("\\s+");
    int attempts = Math.min(MAX_ATTEMPTS, tokens.length - 1);
    List<String> variants = new ArrayList<>(attempts);
    for (int drop = 1; drop <= attempts; drop++) {
      variants.add(String.join(" ", Arrays.asList(tokens).subList(0, tokens.length - drop)));
    }
    return variants;
  }
}
