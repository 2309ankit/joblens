package com.ankit.joblens.intelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class FuzzyDuplicateDetectionTasklet implements Tasklet {

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
    List<DuplicateJobView> jobs = repository.findJobs();
    Map<Long, Long> exactMemberships = repository.findExactMemberships();
    List<JobSimilarity> similarities = new ArrayList<>();

    for (int leftIndex = 0; leftIndex < jobs.size(); leftIndex++) {
      DuplicateJobView left = jobs.get(leftIndex);
      for (int rightIndex = leftIndex + 1; rightIndex < jobs.size(); rightIndex++) {
        DuplicateJobView right = jobs.get(rightIndex);
        if (sameExactCluster(exactMemberships, left.id(), right.id())) {
          continue;
        }
        if (!calculator.isCandidate(left, right)) {
          continue;
        }
        contribution.incrementReadCount();
        JobSimilarity similarity = calculator.calculate(left, right);
        if (calculator.meetsMinimum(similarity)) {
          similarities.add(similarity);
        }
      }
    }

    repository.reconcileSimilarities(FuzzySimilarityCalculator.ALGORITHM_VERSION, similarities);
    contribution.incrementWriteCount(similarities.size());
    if (failThisExecution) {
      throw new InjectedFuzzyDetectionFailureException();
    }
    return RepeatStatus.FINISHED;
  }

  private static boolean sameExactCluster(Map<Long, Long> memberships, long leftId, long rightId) {
    Long leftCluster = memberships.get(leftId);
    return leftCluster != null && leftCluster.equals(memberships.get(rightId));
  }
}
