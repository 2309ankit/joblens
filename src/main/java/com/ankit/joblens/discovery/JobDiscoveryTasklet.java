package com.ankit.joblens.discovery;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class JobDiscoveryTasklet implements Tasklet {

  static final String CURRENT_PROFILE = "discovery.currentProfileId";
  static final String LAST_COMPLETED_PROFILE = "discovery.lastCompletedProfileId";
  static final String FETCH_RUN_ID = "discovery.fetchRunId";
  static final String NEXT_PAGE = "discovery.nextPage";

  private static final Logger log = LoggerFactory.getLogger(JobDiscoveryTasklet.class);

  private final DiscoveryPersistenceService persistence;
  private final List<JobSourceClient> clients;
  private final AdzunaProperties properties;
  private final String requestedProfileId;
  private final UUID workspaceId;

  public JobDiscoveryTasklet(
      DiscoveryPersistenceService persistence,
      List<JobSourceClient> clients,
      AdzunaProperties properties,
      String requestedProfileId,
      String workspaceId) {
    this.persistence = persistence;
    this.clients = clients;
    this.properties = properties;
    this.requestedProfileId = requestedProfileId;
    this.workspaceId = workspaceId == null ? null : UUID.fromString(workspaceId);
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    var stepExecution = chunkContext.getStepContext().getStepExecution();
    var jobExecution = stepExecution.getJobExecution();
    ExecutionContext context = stepExecution.getExecutionContext();
    long jobExecutionId = jobExecution.getId();
    long jobInstanceId = jobExecution.getJobInstanceId();

    String currentProfileId = context.getString(CURRENT_PROFILE, null);
    SearchProfile profile;
    long fetchRunId;
    int nextPage;

    if (currentProfileId == null) {
      String lastCompleted = context.getString(LAST_COMPLETED_PROFILE, null);
      profile = persistence.findNextActiveProfile(lastCompleted, requestedProfileId, workspaceId);
      if (profile == null) {
        return RepeatStatus.FINISHED;
      }
      FetchRunState fetchRun =
          persistence.startOrResumeFetchRun(profile, jobInstanceId, jobExecutionId);
      fetchRunId = fetchRun.id();
      nextPage = fetchRun.nextPage();
      context.putString(CURRENT_PROFILE, profile.profileId());
      context.putLong(FETCH_RUN_ID, fetchRunId);
      context.putInt(NEXT_PAGE, nextPage);
    } else {
      profile = persistence.findNextActiveProfile(null, currentProfileId, workspaceId);
      if (profile == null || !profile.profileId().equals(currentProfileId)) {
        throw new IllegalStateException(
            "Active discovery profile no longer exists: " + currentProfileId);
      }
      fetchRunId = context.getLong(FETCH_RUN_ID);
      FetchRunState fetchRun =
          persistence.startOrResumeFetchRun(profile, jobInstanceId, jobExecutionId);
      nextPage = Math.max(context.getInt(NEXT_PAGE, 1), fetchRun.nextPage());
      context.putInt(NEXT_PAGE, nextPage);
    }

    JobSource source = JobSource.valueOf(profile.source());
    JobSourceClient client =
        clients.stream()
            .filter(candidate -> candidate.supports(source))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No client supports source " + source));

    try {
      log.info(
          "Fetching source={} profile={} page={} jobExecutionId={}",
          source,
          profile.profileId(),
          nextPage,
          jobExecutionId);
      JobPage page = client.search(profile, new PageRequest(nextPage, client.pageSize()));
      persistence.persistPage(fetchRunId, profile, page, jobExecutionId);

      int maxPages = profile.maxPages() == null ? properties.maxPages() : profile.maxPages();
      boolean complete = page.jobs().isEmpty() || !page.hasMore() || nextPage >= maxPages;
      if (complete) {
        persistence.completeFetchRun(fetchRunId, profile, jobExecutionId);
        context.putString(LAST_COMPLETED_PROFILE, profile.profileId());
        context.remove(CURRENT_PROFILE);
        context.remove(FETCH_RUN_ID);
        context.remove(NEXT_PAGE);
        log.info(
            "Completed source={} profile={} pagesBound={} jobExecutionId={}",
            source,
            profile.profileId(),
            maxPages,
            jobExecutionId);
        if (requestedProfileId != null) {
          return RepeatStatus.FINISHED;
        }
      } else {
        context.putInt(NEXT_PAGE, nextPage + 1);
      }
      return RepeatStatus.CONTINUABLE;
    } catch (RuntimeException failure) {
      persistence.failFetchRun(fetchRunId, profile, jobExecutionId, failure);
      log.warn(
          "Failed source={} profile={} page={} jobExecutionId={} reason={}",
          source,
          profile.profileId(),
          nextPage,
          jobExecutionId,
          failure.getMessage());
      throw failure;
    }
  }
}
