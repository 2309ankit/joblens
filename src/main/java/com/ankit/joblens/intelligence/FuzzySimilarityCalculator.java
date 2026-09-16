package com.ankit.joblens.intelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class FuzzySimilarityCalculator {

  public static final String ALGORITHM_VERSION = "fuzzy-v1";

  private static final Set<String> BLOCK_STOP_WORDS =
      Set.of(
          "senior",
          "junior",
          "lead",
          "principal",
          "staff",
          "engineer",
          "developer",
          "software",
          "manager",
          "specialist",
          "role",
          "the",
          "and",
          "for",
          "with");
  private static final Set<String> TEXT_STOP_WORDS =
      Set.of("a", "an", "the", "and", "or", "for", "to", "of", "in", "on", "at", "with", "using");

  private final BigDecimal minimumScore;
  private final BigDecimal likelyScore;

  public FuzzySimilarityCalculator(BigDecimal minimumScore, BigDecimal likelyScore) {
    if (minimumScore.signum() < 0 || minimumScore.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("minimumScore must be between 0 and 100");
    }
    if (likelyScore.compareTo(minimumScore) < 0
        || likelyScore.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("likelyScore must be between minimumScore and 100");
    }
    this.minimumScore = minimumScore;
    this.likelyScore = likelyScore;
  }

  public boolean isCandidate(DuplicateJobView left, DuplicateJobView right) {
    return isCandidate(prepare(left), prepare(right));
  }

  public JobSimilarity calculate(DuplicateJobView left, DuplicateJobView right) {
    return calculate(prepare(left), prepare(right));
  }

  /** Immutable text features, prepared once per corpus snapshot rather than once per pair. */
  public record PreparedJob(
      DuplicateJobView job,
      Set<String> blockingTokens,
      Set<String> titleTokens,
      Set<String> titleTrigrams,
      String company,
      Set<String> companyTokens,
      Set<String> companyTrigrams,
      Set<String> descriptionTokens,
      Set<String> locationTokens,
      String employment) {}

  public PreparedJob prepare(DuplicateJobView job) {
    return new PreparedJob(
        job,
        tokens(job.title(), BLOCK_STOP_WORDS),
        tokens(job.title(), TEXT_STOP_WORDS),
        trigrams(normalized(job.title())),
        normalized(job.company()),
        tokens(job.company(), TEXT_STOP_WORDS),
        trigrams(normalized(job.company())),
        tokens(job.description(), TEXT_STOP_WORDS),
        tokens(job.location(), TEXT_STOP_WORDS),
        normalized(job.employmentType()));
  }

  public boolean isCandidate(PreparedJob left, PreparedJob right) {
    return left.blockingTokens().stream().anyMatch(right.blockingTokens()::contains)
        || (hasText(left.job().company()) && left.company().equals(right.company()))
        || dice(left.titleTrigrams(), right.titleTrigrams()) >= 70;
  }

  public JobSimilarity calculate(PreparedJob preparedLeft, PreparedJob preparedRight) {
    DuplicateJobView left = preparedLeft.job();
    DuplicateJobView right = preparedRight.job();
    if (left.id() >= right.id()) {
      throw new IllegalArgumentException("Similarity pairs must be ordered by job ID");
    }

    BigDecimal title =
        BigDecimal.valueOf(jaccard(preparedLeft.titleTokens(), preparedRight.titleTokens()))
            .multiply(BigDecimal.valueOf(0.6))
            .add(
                BigDecimal.valueOf(
                        dice(preparedLeft.titleTrigrams(), preparedRight.titleTrigrams()))
                    .multiply(BigDecimal.valueOf(0.4)))
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal description =
        !hasText(left.description()) || !hasText(right.description())
            ? null
            : BigDecimal.valueOf(
                    jaccard(preparedLeft.descriptionTokens(), preparedRight.descriptionTokens()))
                .setScale(2);
    BigDecimal company =
        !hasText(left.company()) || !hasText(right.company())
            ? null
            : BigDecimal.valueOf(
                    (long) jaccard(preparedLeft.companyTokens(), preparedRight.companyTokens())
                        + dice(preparedLeft.companyTrigrams(), preparedRight.companyTrigrams()))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    BigDecimal location =
        !hasText(left.location()) || !hasText(right.location())
            ? null
            : BigDecimal.valueOf(
                    jaccard(preparedLeft.locationTokens(), preparedRight.locationTokens()))
                .setScale(2);
    int employmentMatchScore =
        preparedLeft.employment().equals(preparedRight.employment()) ? 100 : 0;
    BigDecimal employment =
        !hasText(left.employmentType()) || !hasText(right.employmentType())
            ? null
            : BigDecimal.valueOf(employmentMatchScore).setScale(2);

    WeightedScore weighted = weightedScore(title, description, company, location, employment);
    String decision =
        weighted.score().compareTo(likelyScore) >= 0 ? "LIKELY_DUPLICATE" : "POSSIBLE_DUPLICATE";
    String explanation =
        "title=%s, description=%s, company=%s, location=%s, employment=%s; "
                .formatted(
                    display(title),
                    display(description),
                    display(company),
                    display(location),
                    display(employment))
            + "weightedScore="
            + weighted.score()
            + ", effectiveWeight="
            + weighted.effectiveWeight()
            + ", decision="
            + decision;

    return new JobSimilarity(
        left.id(),
        right.id(),
        ALGORITHM_VERSION,
        weighted.score(),
        title,
        description,
        company,
        location,
        employment,
        decision,
        explanation,
        left.normalizedContentHash(),
        right.normalizedContentHash());
  }

  public boolean meetsMinimum(JobSimilarity similarity) {
    return similarity.overallScore().compareTo(minimumScore) >= 0;
  }

  private static WeightedScore weightedScore(
      BigDecimal title,
      BigDecimal description,
      BigDecimal company,
      BigDecimal location,
      BigDecimal employment) {
    BigDecimal points = title.multiply(BigDecimal.valueOf(40));
    int weight = 40;
    if (description != null) {
      points = points.add(description.multiply(BigDecimal.valueOf(25)));
      weight += 25;
    }
    if (company != null) {
      points = points.add(company.multiply(BigDecimal.valueOf(20)));
      weight += 20;
    }
    if (location != null) {
      points = points.add(location.multiply(BigDecimal.valueOf(10)));
      weight += 10;
    }
    if (employment != null) {
      points = points.add(employment.multiply(BigDecimal.valueOf(5)));
      weight += 5;
    }
    return new WeightedScore(
        points.divide(BigDecimal.valueOf(weight), 2, RoundingMode.HALF_UP), weight);
  }

  private static int intersectionSize(Set<String> left, Set<String> right) {
    Set<String> smaller = left.size() <= right.size() ? left : right;
    Set<String> larger = left.size() <= right.size() ? right : left;
    int count = 0;
    for (String value : smaller) {
      if (larger.contains(value)) count++;
    }
    return count;
  }

  private static int jaccard(Set<String> left, Set<String> right) {
    if (left.isEmpty() || right.isEmpty()) return 0;
    int intersection = intersectionSize(left, right);
    return (int) Math.round(intersection * 100.0 / (left.size() + right.size() - intersection));
  }

  private static int dice(Set<String> left, Set<String> right) {
    if (left.isEmpty() || right.isEmpty()) return 0;
    return (int) Math.round(intersectionSize(left, right) * 200.0 / (left.size() + right.size()));
  }

  private static Set<String> tokens(String value, Set<String> stopWords) {
    return Arrays.stream(normalized(value).split(" "))
        .filter(token -> token.length() > 1)
        .filter(token -> !stopWords.contains(token))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Set<String> trigrams(String value) {
    String compact = value.replace(" ", "_");
    Set<String> result = new LinkedHashSet<>();
    for (int index = 0; index + 3 <= compact.length(); index++) {
      result.add(compact.substring(index, index + 3));
    }
    return result;
  }

  private static String normalized(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String display(BigDecimal score) {
    return score == null ? "not-compared" : score.toPlainString();
  }

  private record WeightedScore(BigDecimal score, int effectiveWeight) {}
}
