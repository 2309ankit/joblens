package com.ankit.joblens.intelligence;

import com.ankit.joblens.intelligence.FuzzySimilarityCalculator.PreparedJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class FuzzyDuplicateDetectionTasklet implements Tasklet {

  private static final String LAST_LEFT_ID = "fuzzy.v2.lastLeftId";
  static final int LEFT_JOBS_PER_CHUNK = 25;
  private List<PreparedJob> jobs;
  private Map<Long, Long> exactMemberships;
  private FuzzyCandidateIndex index;
  private int nextLeft;

  private final DuplicateDetectionRepository repository;
  private final FuzzySimilarityCalculator calculator;
  private final boolean failThisExecution;

  public FuzzyDuplicateDetectionTasklet(
      DuplicateDetectionRepository repository,
      FuzzySimilarityCalculator calculator,
      boolean failThisExecution) {
    this.repository = repository;
    this.calculator = calculator;
    this.failThisExecution = failThisExecution;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    var context = chunkContext.getStepContext().getStepExecution().getExecutionContext();
    if (jobs == null) {
      jobs = repository.findJobs().stream().map(calculator::prepare).toList();
      exactMemberships = repository.findExactMemberships();
      index = new FuzzyCandidateIndex(jobs);
      long lastLeft = context.getLong(LAST_LEFT_ID, -1L);
      while (nextLeft < jobs.size() && jobs.get(nextLeft).job().id() <= lastLeft) nextLeft++;
    }
    if (nextLeft >= jobs.size()) return RepeatStatus.FINISHED;

    int end = Math.min(nextLeft + LEFT_JOBS_PER_CHUNK, jobs.size());
    List<JobSimilarity> similarities = new ArrayList<>();
    List<Long> leftIds = new ArrayList<>();
    for (int leftIndex = nextLeft; leftIndex < end; leftIndex++) {
      PreparedJob left = jobs.get(leftIndex);
      leftIds.add(left.job().id());
      for (int rightIndex : index.candidates(leftIndex)) {
        PreparedJob right = jobs.get(rightIndex);
        if (sameExactCluster(exactMemberships, left.job().id(), right.job().id())) continue;
        contribution.incrementReadCount();
        JobSimilarity similarity = calculator.calculate(left, right);
        if (calculator.meetsMinimum(similarity)) similarities.add(similarity);
      }
    }
    // CPU work above holds no transaction. A crash after this commit safely replays one chunk.
    repository.reconcileSimilaritiesForLeftJobs(
        leftIds, FuzzySimilarityCalculator.ALGORITHM_VERSION, similarities, failThisExecution);
    contribution.incrementWriteCount(similarities.size());
    context.putLong(LAST_LEFT_ID, leftIds.getLast());
    nextLeft = end;
    return nextLeft >= jobs.size() ? RepeatStatus.FINISHED : RepeatStatus.CONTINUABLE;
  }

  private static boolean sameExactCluster(Map<Long, Long> memberships, long leftId, long rightId) {
    Long leftCluster = memberships.get(leftId);
    return leftCluster != null && leftCluster.equals(memberships.get(rightId));
  }
}
