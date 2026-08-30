package com.ankit.joblens.intelligence;

import com.ankit.joblens.discovery.JobSource;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JoobleJobPostingNormalizer implements JobPostingNormalizer {
  private final ObjectMapper objectMapper;
  private final HtmlTextCleaner htmlTextCleaner;
  private final NormalizedContentHasher contentHasher;

  public JoobleJobPostingNormalizer(
      ObjectMapper objectMapper,
      HtmlTextCleaner htmlTextCleaner,
      NormalizedContentHasher contentHasher) {
    this.objectMapper = objectMapper;
    this.htmlTextCleaner = htmlTextCleaner;
    this.contentHasher = contentHasher;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.JOOBLE;
  }

  @Override
  public NormalizedJob normalize(RawJobPosting raw) {
    JsonNode root = parse(raw);
    String title = text(root.get("title"));
    if (title == null) {
      throw new NormalizationRejectedException(raw, "Jooble job title is required");
    }
    NormalizedJob job =
        new NormalizedJob(
            raw.id(),
            JobSource.JOOBLE.name(),
            raw.externalJobId(),
            title,
            text(root.get("company")),
            text(root.get("location")),
            htmlTextCleaner.clean(text(root.get("snippet"))),
            employmentType(text(root.get("type"))),
            null,
            null,
            null,
            null,
            timestamp(text(root.get("updated"))),
            sourceUrl(root, raw),
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
        throw new NormalizationRejectedException(raw, "Raw Jooble JSON must be an object");
      }
      return root;
    } catch (NormalizationRejectedException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new NormalizationRejectedException(raw, "Raw Jooble JSON is malformed", exception);
    }
  }

  private static String sourceUrl(JsonNode root, RawJobPosting raw) {
    String link = text(root.get("link"));
    return link == null ? raw.sourceUrl() : link;
  }

  private static String employmentType(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    if (normalized.contains("FULLTIME")) {
      return "PERMANENT";
    }
    if (normalized.contains("CONTRACT")) {
      return "CONTRACT";
    }
    return null;
  }

  private static OffsetDateTime timestamp(String value) {
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private static String text(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asString().trim().replaceAll("\\s+", " ");
    return value.isEmpty() ? null : value;
  }
}
