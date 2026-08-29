package com.ankit.joblens.intelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class FuzzySimilarityCalculator {

    public static final String ALGORITHM_VERSION = "fuzzy-v1";

    private static final Set<String> BLOCK_STOP_WORDS = Set.of(
            "senior", "junior", "lead", "principal", "staff", "engineer", "developer",
            "software", "manager", "specialist", "role", "the", "and", "for", "with");
    private static final Set<String> TEXT_STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "for", "to", "of", "in", "on", "at", "with", "using");

    private final BigDecimal minimumScore;
    private final BigDecimal likelyScore;

    public FuzzySimilarityCalculator(BigDecimal minimumScore, BigDecimal likelyScore) {
        if (minimumScore.signum() < 0 || minimumScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("minimumScore must be between 0 and 100");
        }
        if (likelyScore.compareTo(minimumScore) < 0 || likelyScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("likelyScore must be between minimumScore and 100");
        }
        this.minimumScore = minimumScore;
        this.likelyScore = likelyScore;
    }

    public boolean isCandidate(DuplicateJobView left, DuplicateJobView right) {
        Set<String> leftTitle = tokens(left.title(), BLOCK_STOP_WORDS);
        Set<String> rightTitle = tokens(right.title(), BLOCK_STOP_WORDS);
        boolean titleTokenOverlap = leftTitle.stream().anyMatch(rightTitle::contains);
        boolean companyExact = hasText(left.company()) && normalized(left.company()).equals(normalized(right.company()));
        return titleTokenOverlap || companyExact || trigramDice(left.title(), right.title()) >= 70;
    }

    public JobSimilarity calculate(DuplicateJobView left, DuplicateJobView right) {
        if (left.id() >= right.id()) {
            throw new IllegalArgumentException("Similarity pairs must be ordered by job ID");
        }

        BigDecimal title = scoreTitle(left.title(), right.title());
        BigDecimal description = optionalTokenScore(left.description(), right.description());
        BigDecimal company = optionalCombinedScore(left.company(), right.company());
        BigDecimal location = optionalTokenScore(left.location(), right.location());
        BigDecimal employment = optionalExactScore(left.employmentType(), right.employmentType());

        WeightedScore weighted = weightedScore(title, description, company, location, employment);
        String decision = weighted.score().compareTo(likelyScore) >= 0
                ? "LIKELY_DUPLICATE" : "POSSIBLE_DUPLICATE";
        String explanation = "title=%s, description=%s, company=%s, location=%s, employment=%s; "
                .formatted(display(title), display(description), display(company), display(location), display(employment))
                + "weightedScore=" + weighted.score() + ", effectiveWeight=" + weighted.effectiveWeight()
                + ", decision=" + decision;

        return new JobSimilarity(
                left.id(), right.id(), ALGORITHM_VERSION, weighted.score(), title, description,
                company, location, employment, decision, explanation,
                left.normalizedContentHash(), right.normalizedContentHash());
    }

    public boolean meetsMinimum(JobSimilarity similarity) {
        return similarity.overallScore().compareTo(minimumScore) >= 0;
    }

    private static WeightedScore weightedScore(BigDecimal title, BigDecimal description,
            BigDecimal company, BigDecimal location, BigDecimal employment) {
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
        return new WeightedScore(points.divide(BigDecimal.valueOf(weight), 2, RoundingMode.HALF_UP), weight);
    }

    private static BigDecimal scoreTitle(String left, String right) {
        int tokenScore = tokenJaccard(left, right);
        int trigramScore = trigramDice(left, right);
        return BigDecimal.valueOf(tokenScore).multiply(BigDecimal.valueOf(0.6))
                .add(BigDecimal.valueOf(trigramScore).multiply(BigDecimal.valueOf(0.4)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal optionalCombinedScore(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return null;
        }
        return BigDecimal.valueOf(tokenJaccard(left, right) + trigramDice(left, right))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal optionalTokenScore(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return null;
        }
        return BigDecimal.valueOf(tokenJaccard(left, right)).setScale(2);
    }

    private static BigDecimal optionalExactScore(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return null;
        }
        return normalized(left).equals(normalized(right))
                ? BigDecimal.valueOf(100).setScale(2) : BigDecimal.ZERO.setScale(2);
    }

    private static int tokenJaccard(String left, String right) {
        Set<String> leftTokens = tokens(left, TEXT_STOP_WORDS);
        Set<String> rightTokens = tokens(right, TEXT_STOP_WORDS);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return (int) Math.round(intersection.size() * 100.0 / union.size());
    }

    private static int trigramDice(String left, String right) {
        Set<String> leftTrigrams = trigrams(normalized(left));
        Set<String> rightTrigrams = trigrams(normalized(right));
        if (leftTrigrams.isEmpty() || rightTrigrams.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(leftTrigrams);
        intersection.retainAll(rightTrigrams);
        return (int) Math.round(intersection.size() * 200.0
                / (leftTrigrams.size() + rightTrigrams.size()));
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

    private record WeightedScore(BigDecimal score, int effectiveWeight) {
    }
}
