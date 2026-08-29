package com.ankit.joblens.lifecycle;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.ankit.joblens.jdbc.ClasspathSql;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationLifecycleRepository {

    private static final String FIND_DEFAULT_CANDIDATE =
            ClasspathSql.load("sql/lifecycle/find-default-candidate.sql");
    private static final String FIND_JOB = ClasspathSql.load("sql/lifecycle/find-job.sql");
    private static final String CREATE_APPLICATION =
            ClasspathSql.load("sql/lifecycle/create-application.sql");
    private static final String INSERT_HISTORY = ClasspathSql.load("sql/lifecycle/insert-history.sql");
    private static final String FIND_APPLICATION_FOR_UPDATE =
            ClasspathSql.load("sql/lifecycle/find-application-for-update.sql");
    private static final String UPDATE_APPLICATION_STATUS =
            ClasspathSql.load("sql/lifecycle/update-application-status.sql");
    private static final String FIND_CURRENT_APPLICATIONS =
            ClasspathSql.load("sql/lifecycle/find-current-applications.sql");
    private static final String CANCEL_STALE_FOLLOW_UPS =
            ClasspathSql.load("sql/lifecycle/cancel-stale-follow-ups.sql");
    private static final String CANCEL_OPEN_FOLLOW_UPS =
            ClasspathSql.load("sql/lifecycle/cancel-open-follow-ups.sql");
    private static final String UPSERT_FOLLOW_UP =
            ClasspathSql.load("sql/lifecycle/upsert-follow-up.sql");
    private static final String FIND_FOLLOW_UP_FOR_UPDATE =
            ClasspathSql.load("sql/lifecycle/find-follow-up-for-update.sql");
    private static final String COMPLETE_FOLLOW_UP =
            ClasspathSql.load("sql/lifecycle/complete-follow-up.sql");

    private final NamedParameterJdbcTemplate jdbc;

    public ApplicationLifecycleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean jobExists(long jobId) {
        return !jdbc.queryForList(FIND_JOB, Map.of("jobId", jobId), Long.class).isEmpty();
    }

    public long findDefaultCandidateId() {
        List<Long> ids = jdbc.queryForList(FIND_DEFAULT_CANDIDATE, Map.of(), Long.class);
        if (ids.isEmpty()) {
            throw new LifecycleNotFoundException("No candidate profile is configured");
        }
        return ids.getFirst();
    }

    public long createApplication(long jobId, long candidateProfileId, LocalDate effectiveDate, String note) {
        try {
            return jdbc.queryForObject(CREATE_APPLICATION, new MapSqlParameterSource()
                    .addValue("jobId", jobId)
                    .addValue("candidateProfileId", candidateProfileId)
                    .addValue("effectiveDate", effectiveDate)
                    .addValue("note", note), Long.class);
        }
        catch (DuplicateKeyException exception) {
            throw new LifecycleConflictException("An application already exists for this job and candidate");
        }
    }

    public long insertHistory(long applicationId, ApplicationStatus from, ApplicationStatus to,
            LocalDate effectiveDate, String note) {
        return jdbc.queryForObject(INSERT_HISTORY, new MapSqlParameterSource()
                .addValue("applicationId", applicationId)
                .addValue("fromStatus", from == null ? null : from.name())
                .addValue("toStatus", to.name())
                .addValue("effectiveDate", effectiveDate)
                .addValue("note", note), Long.class);
    }

    public ApplicationRecord findApplicationForUpdate(long id) {
        List<ApplicationRecord> applications = jdbc.query(FIND_APPLICATION_FOR_UPDATE, Map.of("id", id),
                (rs, rowNum) -> new ApplicationRecord(
                        rs.getLong("id"), rs.getLong("normalized_job_id"),
                        rs.getLong("candidate_profile_id"),
                        ApplicationStatus.valueOf(rs.getString("status")),
                        rs.getObject("status_effective_date", LocalDate.class),
                        rs.getObject("applied_on", LocalDate.class), rs.getString("note")));
        return applications.isEmpty() ? null : applications.getFirst();
    }

    public void updateStatus(long id, ApplicationStatus status, LocalDate effectiveDate, String note) {
        jdbc.update(UPDATE_APPLICATION_STATUS, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("effectiveDate", effectiveDate)
                .addValue("note", note));
    }

    public List<FollowUpApplication> findCurrentApplications(LocalDate businessDate) {
        return jdbc.query(FIND_CURRENT_APPLICATIONS, Map.of("businessDate", businessDate),
                (rs, rowNum) -> new FollowUpApplication(
                        rs.getLong("id"), ApplicationStatus.valueOf(rs.getString("status")),
                        rs.getObject("status_effective_date", LocalDate.class), rs.getLong("history_id")));
    }

    public void cancelStaleFollowUps(long applicationId, long currentHistoryId) {
        jdbc.update(CANCEL_STALE_FOLLOW_UPS, Map.of(
                "applicationId", applicationId, "historyId", currentHistoryId));
    }

    public void cancelOpenFollowUps(long applicationId) {
        jdbc.update(CANCEL_OPEN_FOLLOW_UPS, Map.of("applicationId", applicationId));
    }

    public void upsertFollowUp(FollowUpApplication application, FollowUpPlan plan,
            String generationVersion) {
        jdbc.update(UPSERT_FOLLOW_UP, new MapSqlParameterSource()
                .addValue("applicationId", application.applicationId())
                .addValue("historyId", application.historyId())
                .addValue("followUpType", plan.type().name())
                .addValue("generationVersion", generationVersion)
                .addValue("dueDate", application.statusEffectiveDate().plusDays(plan.daysAfterStatus())));
    }

    public FollowUpRecord findFollowUpForUpdate(long id) {
        List<FollowUpRecord> followUps = jdbc.query(FIND_FOLLOW_UP_FOR_UPDATE, Map.of("id", id),
                (rs, rowNum) -> new FollowUpRecord(
                        rs.getLong("id"), rs.getLong("application_id"), rs.getString("status"),
                        rs.getObject("due_date", LocalDate.class)));
        return followUps.isEmpty() ? null : followUps.getFirst();
    }

    public void completeFollowUp(long id, LocalDate completedOn) {
        jdbc.update(COMPLETE_FOLLOW_UP, Map.of("id", id, "completedOn", completedOn));
    }
}
