package com.ankit.joblens.intelligence;

import com.ankit.joblens.discovery.JobSource;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LeverJobPostingNormalizer implements JobPostingNormalizer {
  private final ObjectMapper objectMapper;
  private final HtmlTextCleaner htmlTextCleaner;
  private final NormalizedContentHasher contentHasher;

  public LeverJobPostingNormalizer(
      ObjectMapper objectMapper,
      HtmlTextCleaner htmlTextCleaner,
      NormalizedContentHasher contentHasher) {
    this.objectMapper = objectMapper;
    this.htmlTextCleaner = htmlTextCleaner;
    this.contentHasher = contentHasher;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.LEVER;
  }

  @Override
  public NormalizedJob normalize(RawJobPosting raw) {
    JsonNode root = parse(raw);
    String title = text(root.get("text"));
    if (title == null) {
      throw new NormalizationRejectedException(raw, "Lever job title is required");
    }
    JsonNode categories = root.path("categories");
    JsonNode salary = root.path("salaryRange");
    NormalizedJob job =
        new NormalizedJob(
            raw.id(),
            JobSource.LEVER.name(),
            raw.externalJobId(),
            title,
            null,
            text(categories.get("location")),
            htmlTextCleaner.clean(description(root)),
            employment(text(categories.get("commitment"))),
            decimal(salary.get("min")),
            decimal(salary.get("max")),
            currency(salary.get("currency")),
            remoteType(text(root.get("workplaceType"))),
            null,
            valueOrFallback(text(root.get("hostedUrl")), raw.sourceUrl()),
            null);
    return new NormalizedJob(
        job.rawJobPostingId(),
        job.source(),
        job.externalJobId(),
        job.title(),
        job.company(),
        job.location(),
        job.descriptionText(),
        job.employmentType(),
        job.salaryMin(),
        job.salaryMax(),
        job.salaryCurrency(),
        job.remoteType(),
        job.postedAt(),
        job.sourceUrl(),
        contentHasher.hash(job));
  }

  private JsonNode parse(RawJobPosting raw) {
    try {
      JsonNode root = objectMapper.readTree(raw.rawJson());
      if (root == null || !root.isObject()) {
        throw new NormalizationRejectedException(raw, "Raw Lever JSON must be an object");
      }
      return root;
    } catch (NormalizationRejectedException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new NormalizationRejectedException(raw, "Raw Lever JSON is malformed", exception);
    }
  }

  private static String description(JsonNode root) {
    StringBuilder value = new StringBuilder(valueOrFallback(text(root.get("description")), ""));
    JsonNode lists = root.get("lists");
    if (lists != null && lists.isArray()) {
      for (JsonNode list : lists) {
        value.append(' ').append(valueOrFallback(text(list.get("text")), ""));
        value.append(' ').append(valueOrFallback(text(list.get("content")), ""));
      }
    }
    value.append(' ').append(valueOrFallback(text(root.get("additional")), ""));
    return value.toString();
  }

  private static String employment(String value) {
    if (value == null) {
      return null;
    }
    return switch (value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "")) {
      case "fulltime", "permanent" -> "PERMANENT";
      case "contract" -> "CONTRACT";
      case "parttime" -> "PART_TIME";
      case "temporary", "temp" -> "TEMPORARY";
      default -> null;
    };
  }

  private static String remoteType(String value) {
    if (value == null) {
      return null;
    }
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "remote" -> "REMOTE";
      case "hybrid" -> "HYBRID";
      case "on-site" -> "ONSITE";
      default -> null;
    };
  }

  private static BigDecimal decimal(JsonNode node) {
    return node == null || !node.isNumber() ? null : node.decimalValue();
  }

  private static String currency(JsonNode node) {
    String value = text(node);
    return value != null && value.matches("[A-Za-z]{3}") ? value.toUpperCase(Locale.ROOT) : null;
  }

  private static String text(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asString().trim();
    return value.isEmpty() ? null : value;
  }

  private static String valueOrFallback(String value, String fallback) {
    return value == null ? fallback : value;
  }
}
