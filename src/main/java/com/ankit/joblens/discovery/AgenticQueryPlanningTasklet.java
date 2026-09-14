package com.ankit.joblens.discovery;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class AgenticQueryPlanningTasklet implements Tasklet {
  private static final Logger log = LoggerFactory.getLogger(AgenticQueryPlanningTasklet.class);

  private final QueryPlanningRepository repository;
  private final QueryPlanningClient client;
  private final LlmQueryPlanningProperties properties;
  private final UUID workspaceId;

  public AgenticQueryPlanningTasklet(
      QueryPlanningRepository repository,
      QueryPlanningClient client,
      LlmQueryPlanningProperties properties,
      String workspaceId) {
    this.repository = repository;
    this.client = client;
    this.properties = properties;
    this.workspaceId = workspaceId == null ? null : UUID.fromString(workspaceId);
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    if (!properties.enabled() || workspaceId == null) {
      return RepeatStatus.FINISHED;
    }
    properties.requireEnabledConfiguration();
    long jobExecutionId =
        chunkContext.getStepContext().getStepExecution().getJobExecution().getId();
    int remainingDailyCalls =
        properties.maxCallsPerDay() - repository.callsToday(properties.model());
    if (remainingDailyCalls <= 0) {
      log.warn("Query planning daily call limit reached for model={}", properties.model());
      return RepeatStatus.FINISHED;
    }

    int calls = 0;
    for (SearchProfile profile :
        repository.activeProfiles(workspaceId, properties.maxProfilesPerRun())) {
      contribution.incrementReadCount();
      if (calls >= remainingDailyCalls) {
        log.warn("Query planning daily call limit reached for model={}", properties.model());
        break;
      }
      calls++;
      QueryPlanningRepository.LatestOutcome outcome = repository.latestOutcome(profile.profileId());
      QueryPlanCandidate candidate =
          new QueryPlanCandidate(
              profile.profileId(),
              profile.source(),
              profile.keywords(),
              properties.maxPagesCeiling(),
              outcome == null ? null : outcome.status(),
              outcome == null ? null : outcome.recordsReceived());
      try {
        QueryPlanResult result = client.plan(candidate);
        repository.recordSuccess(
            jobExecutionId, profile.profileId(), profile.keywords(), properties, result);
        contribution.incrementWriteCount(1);
      } catch (RuntimeException failure) {
        String reason = sanitize(failure);
        repository.recordFailure(jobExecutionId, profile.profileId(), properties, reason);
        log.warn(
            "Query planning failed for searchProfileId={}; deterministic query remains active: {}",
            profile.profileId(),
            reason);
      }
    }
    return RepeatStatus.FINISHED;
  }

  private static String sanitize(Throwable failure) {
    String message = failure.getMessage();
    String reason =
        failure.getClass().getSimpleName()
            + ": "
            + (message == null ? "No failure message" : message);
    reason = reason.replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[REDACTED]");
    return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
  }
}
