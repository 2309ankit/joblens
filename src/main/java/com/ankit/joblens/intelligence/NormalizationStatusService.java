package com.ankit.joblens.intelligence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NormalizationStatusService {

    private final JdbcTemplate jdbcTemplate;

    public NormalizationStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(long rawJobPostingId, String reason) {
        update(rawJobPostingId, "REJECTED", reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(long rawJobPostingId, Throwable failure) {
        String message = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "No failure message" : failure.getMessage());
        update(rawJobPostingId, "FAILED", message);
    }

    private void update(long rawJobPostingId, String status, String reason) {
        String boundedReason = reason.length() > 2000 ? reason.substring(0, 2000) : reason;
        jdbcTemplate.update("""
                UPDATE raw_job_posting
                SET processing_status = ?, processing_reason = ?, processed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, boundedReason, rawJobPostingId);
    }
}
