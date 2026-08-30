package com.ankit.joblens.searchprofile;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class SearchProfileRejectionListener
    implements SkipListener<SearchProfileCsvRow, SearchProfile>, StepExecutionListener {

  private static final String INSERT_SQL =
      """
            INSERT INTO search_profile_rejection (
                input_file, row_number, raw_record, rejection_reason, job_execution_id
            ) VALUES (?, ?, CAST(? AS jsonb), ?, ?)
            """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final String inputFile;
  private long jobExecutionId;

  public SearchProfileRejectionListener(
      JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, String inputFile) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.inputFile = inputFile;
  }

  @Override
  public void beforeStep(StepExecution stepExecution) {
    jobExecutionId = stepExecution.getJobExecutionId();
  }

  @Override
  public void onSkipInProcess(SearchProfileCsvRow row, Throwable throwable) {
    persist(row.rowNumber(), rowAsJson(row), throwable.getMessage());
  }

  @Override
  public void onSkipInRead(Throwable throwable) {
    if (throwable instanceof FlatFileParseException parseException) {
      persist(
          (long) parseException.getLineNumber(),
          lineAsJson(parseException.getInput()),
          "CSV parsing failure: " + parseException.getMessage());
    }
  }

  private void persist(Long rowNumber, String rawJson, String reason) {
    jdbcTemplate.update(INSERT_SQL, inputFile, rowNumber, rawJson, reason, jobExecutionId);
  }

  private String rowAsJson(SearchProfileCsvRow row) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("profile_id", row.profileId());
    values.put("source", row.source());
    values.put("source_key", row.sourceKey());
    values.put("keywords", row.keywords());
    values.put("location", row.location());
    values.put("include_skills", row.includeSkills());
    values.put("exclude_skills", row.excludeSkills());
    values.put("employment_type", row.employmentType());
    values.put("active", row.active());
    return toJson(values);
  }

  private String lineAsJson(String line) {
    return toJson(Map.of("line", line == null ? "" : line));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Could not serialize rejected CSV input", exception);
    }
  }
}
