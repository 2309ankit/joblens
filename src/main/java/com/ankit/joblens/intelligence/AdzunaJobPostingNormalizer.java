package com.ankit.joblens.intelligence;

import com.ankit.joblens.discovery.JobSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdzunaJobPostingNormalizer implements JobPostingNormalizer {

  private final ObjectMapper objectMapper;
  private final HtmlTextCleaner htmlTextCleaner;
  private final NormalizedContentHasher contentHasher;

  public AdzunaJobPostingNormalizer(
      ObjectMapper objectMapper,
      HtmlTextCleaner htmlTextCleaner,
      NormalizedContentHasher contentHasher) {
    this.objectMapper = objectMapper;
    this.htmlTextCleaner = htmlTextCleaner;
    this.contentHasher = contentHasher;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.ADZUNA;
  }

  @Override
  public NormalizedJob normalize(RawJobPosting raw) {
    JsonNode root = parse(raw);
    String title = text(root.get("title"));
    if (title == null) {
      throw new NormalizationRejectedException(raw, "Adzuna job title is required");
    }

    BigDecimal salaryMin = decimal(root.get("salary_min"));
    BigDecimal salaryMax = decimal(root.get("salary_max"));
    if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
      salaryMin = null;
      salaryMax = null;
    }

    String payloadUrl = text(root.get("redirect_url"));
    String landedSourceUrl = blankToNull(raw.sourceUrl());
    NormalizedJob withoutHash =
        new NormalizedJob(
            raw.id(),
            JobSource.ADZUNA.name(),
            raw.externalJobId(),
            title,
            nestedText(root, "company", "display_name"),
            nestedText(root, "location", "display_name"),
            htmlTextCleaner.clean(text(root.get("description"))),
            employmentType(root),
            salaryMin,
            salaryMax,
            currency(root.get("salary_currency")),
            remoteType(root.get("remote_type")),
            timestamp(root.get("created")),
            landedSourceUrl != null ? landedSourceUrl : payloadUrl,
            null);
    return new NormalizedJob(
        withoutHash.rawJobPostingId(),
        withoutHash.source(),
        withoutHash.externalJobId(),
        withoutHash.title(),
        withoutHash.company(),
        withoutHash.location(),
        withoutHash.descriptionText(),
        withoutHash.employmentType(),
        withoutHash.salaryMin(),
        withoutHash.salaryMax(),
        withoutHash.salaryCurrency(),
        withoutHash.remoteType(),
        withoutHash.postedAt(),
        withoutHash.sourceUrl(),
        contentHasher.hash(withoutHash));
  }

  private JsonNode parse(RawJobPosting raw) {
    if (raw.rawJson() == null || raw.rawJson().isBlank()) {
      throw new NormalizationRejectedException(raw, "Raw Adzuna JSON is required");
    }
    try {
      JsonNode root = objectMapper.readTree(raw.rawJson());
      if (root == null || !root.isObject()) {
        throw new NormalizationRejectedException(raw, "Raw Adzuna JSON must be an object");
      }
      return root;
    } catch (NormalizationRejectedException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new NormalizationRejectedException(raw, "Raw Adzuna JSON is malformed", exception);
    }
  }

  private static String employmentType(JsonNode root) {
    String contractType = lower(text(root.get("contract_type")));
    if ("permanent".equals(contractType)) {
      return "PERMANENT";
    }
    if ("contract".equals(contractType)) {
      return "CONTRACT";
    }
    if ("temporary".equals(contractType)) {
      return "TEMPORARY";
    }
    String contractTime = lower(text(root.get("contract_time")));
    return "part_time".equals(contractTime) || "part-time".equals(contractTime)
        ? "PART_TIME"
        : null;
  }

  private static String remoteType(JsonNode node) {
    String value = lower(text(node));
    return switch (value == null ? "" : value) {
      case "remote" -> "REMOTE";
      case "hybrid" -> "HYBRID";
      case "onsite", "on-site" -> "ONSITE";
      default -> null;
    };
  }

  private static String currency(JsonNode node) {
    String value = text(node);
    if (value == null || value.length() != 3) {
      return null;
    }
    return value.toUpperCase(Locale.ROOT);
  }

  private static BigDecimal decimal(JsonNode node) {
    if (node == null || node.isNull() || !node.isNumber()) {
      return null;
    }
    BigDecimal value = node.decimalValue();
    return value.signum() >= 0 ? value : null;
  }

  private static OffsetDateTime timestamp(JsonNode node) {
    String value = text(node);
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private static String nestedText(JsonNode root, String objectName, String fieldName) {
    JsonNode object = root.get(objectName);
    return object == null || !object.isObject() ? null : text(object.get(fieldName));
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() || !node.isString() ? null : blankToNull(node.asString());
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.isEmpty() ? null : normalized;
  }

  private static String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }
}
