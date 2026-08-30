package com.ankit.joblens.intelligence;

import com.ankit.joblens.discovery.JobSource;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GreenhouseJobPostingNormalizer implements JobPostingNormalizer {
  private final ObjectMapper objectMapper;
  private final HtmlTextCleaner htmlTextCleaner;
  private final NormalizedContentHasher contentHasher;

  public GreenhouseJobPostingNormalizer(
      ObjectMapper objectMapper,
      HtmlTextCleaner htmlTextCleaner,
      NormalizedContentHasher contentHasher) {
    this.objectMapper = objectMapper;
    this.htmlTextCleaner = htmlTextCleaner;
    this.contentHasher = contentHasher;
  }

  @Override
  public boolean supports(JobSource source) {
    return source == JobSource.GREENHOUSE;
  }

  @Override
  public NormalizedJob normalize(RawJobPosting raw) {
    JsonNode root = parse(raw);
    String title = text(root.get("title"));
    if (title == null) {
      throw new NormalizationRejectedException(raw, "Greenhouse job title is required");
    }
    JsonNode locationNode = root.get("location");
    String location =
        locationNode != null && locationNode.isObject()
            ? text(locationNode.get("name"))
            : text(locationNode);
    String sourceUrl = text(root.get("absolute_url"));
    NormalizedJob job =
        new NormalizedJob(
            raw.id(),
            JobSource.GREENHOUSE.name(),
            raw.externalJobId(),
            title,
            null,
            location,
            htmlTextCleaner.clean(text(root.get("content"))),
            null,
            null,
            null,
            null,
            null,
            timestamp(root.get("updated_at")),
            sourceUrl == null ? raw.sourceUrl() : sourceUrl,
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
        throw new NormalizationRejectedException(raw, "Raw Greenhouse JSON must be an object");
      }
      return root;
    } catch (NormalizationRejectedException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new NormalizationRejectedException(raw, "Raw Greenhouse JSON is malformed", exception);
    }
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

  private static String text(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asString().trim().replaceAll("\\s+", " ");
    return value.isEmpty() ? null : value;
  }
}
