package com.ankit.joblens.intelligence;

import java.sql.Timestamp;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

public class NormalizedJobWriter implements ItemWriter<NormalizedJob> {

  private static final String UPSERT =
      """
            INSERT INTO normalized_job (
                raw_job_posting_id, source, external_job_id, title, company, location,
                description_text, employment_type, salary_min, salary_max, salary_currency,
                remote_type, posted_at, source_url, normalized_content_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (raw_job_posting_id) DO UPDATE SET
                source = EXCLUDED.source,
                external_job_id = EXCLUDED.external_job_id,
                title = EXCLUDED.title,
                company = EXCLUDED.company,
                location = EXCLUDED.location,
                description_text = EXCLUDED.description_text,
                employment_type = EXCLUDED.employment_type,
                salary_min = EXCLUDED.salary_min,
                salary_max = EXCLUDED.salary_max,
                salary_currency = EXCLUDED.salary_currency,
                remote_type = EXCLUDED.remote_type,
                posted_at = EXCLUDED.posted_at,
                source_url = EXCLUDED.source_url,
                normalized_content_hash = EXCLUDED.normalized_content_hash,
                updated_at = CASE
                    WHEN normalized_job.normalized_content_hash <> EXCLUDED.normalized_content_hash
                    THEN CURRENT_TIMESTAMP
                    ELSE normalized_job.updated_at
                END
            """;

  private final JdbcTemplate jdbcTemplate;

  public NormalizedJobWriter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void write(Chunk<? extends NormalizedJob> chunk) {
    for (NormalizedJob job : chunk) {
      jdbcTemplate.update(
          UPSERT,
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
          job.postedAt() == null ? null : Timestamp.from(job.postedAt().toInstant()),
          job.sourceUrl(),
          job.normalizedContentHash());
      jdbcTemplate.update(
          """
                    UPDATE raw_job_posting
                    SET processing_status = 'NORMALIZED', processing_reason = NULL,
                        processed_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
          job.rawJobPostingId());
    }
  }
}
