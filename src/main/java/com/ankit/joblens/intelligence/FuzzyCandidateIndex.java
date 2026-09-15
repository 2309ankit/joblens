package com.ankit.joblens.intelligence;

import com.ankit.joblens.intelligence.FuzzySimilarityCalculator.PreparedJob;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Exact candidate generation for fuzzy-v1's token/company/rounded-trigram OR predicate. */
final class FuzzyCandidateIndex {
  private final List<PreparedJob> jobs;
  private final Map<String, List<Integer>> tokens = new HashMap<>();
  private final Map<String, List<Integer>> companies = new HashMap<>();
  private final Map<String, List<Integer>> trigrams = new HashMap<>();

  FuzzyCandidateIndex(List<PreparedJob> jobs) {
    this.jobs = jobs;
    for (int index = 0; index < jobs.size(); index++) {
      PreparedJob job = jobs.get(index);
      for (String token : job.blockingTokens()) add(tokens, token, index);
      // Preserve punctuation-only nonblank company semantics of fuzzy-v1 as well.
      add(companies, job.company(), index);
      for (String trigram : job.titleTrigrams()) add(trigrams, trigram, index);
    }
  }

  Set<Integer> candidates(int leftIndex) {
    PreparedJob left = jobs.get(leftIndex);
    Set<Integer> result = new TreeSet<>();
    for (String token : left.blockingTokens()) collect(tokens.get(token), leftIndex, result);
    if (left.job().company() != null && !left.job().company().isBlank()) {
      collect(companies.get(left.company()), leftIndex, result);
    }
    Map<Integer, Integer> overlaps = new HashMap<>();
    for (String trigram : left.titleTrigrams()) {
      for (int right : trigrams.get(trigram)) {
        if (right > leftIndex && !result.contains(right)) overlaps.merge(right, 1, Integer::sum);
      }
    }
    overlaps.forEach(
        (right, overlap) -> {
          if (Math.round(
                  overlap
                      * 200.0
                      / (left.titleTrigrams().size() + jobs.get(right).titleTrigrams().size()))
              >= 70) {
            result.add(right);
          }
        });
    return result;
  }

  private static void add(Map<String, List<Integer>> index, String key, int value) {
    index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
  }

  private static void collect(List<Integer> values, int left, Set<Integer> result) {
    if (values != null) for (int right : values) if (right > left) result.add(right);
  }
}
