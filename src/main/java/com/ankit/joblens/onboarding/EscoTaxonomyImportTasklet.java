package com.ankit.joblens.onboarding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class EscoTaxonomyImportTasklet implements Tasklet {
  static final String RELEASE_ID = "esco.releaseId";
  static final String CONCEPT_TYPE = "esco.conceptType";
  static final String NEXT_PAGE = "esco.nextPage";

  private static final Logger log = LoggerFactory.getLogger(EscoTaxonomyImportTasklet.class);

  private final EscoTaxonomyClient client;
  private final EscoTaxonomyRepository repository;
  private final String version;

  public EscoTaxonomyImportTasklet(
      EscoTaxonomyClient client, EscoTaxonomyRepository repository, String version) {
    this.client = client;
    this.repository = repository;
    this.version = version;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    ExecutionContext context =
        chunkContext.getStepContext().getStepExecution().getExecutionContext();
    long releaseId;
    if (!context.containsKey(RELEASE_ID)) {
      EscoTaxonomyRepository.Release release = repository.startOrResume(version);
      if (release.complete()) {
        return RepeatStatus.FINISHED;
      }
      releaseId = release.id();
      context.putLong(RELEASE_ID, releaseId);
      context.putString(CONCEPT_TYPE, "skill");
      context.putInt(NEXT_PAGE, 0);
    } else {
      releaseId = context.getLong(RELEASE_ID);
    }

    String conceptType = context.getString(CONCEPT_TYPE, "skill");
    int pageNumber = context.getInt(NEXT_PAGE, 0);
    try {
      log.info("Fetching ESCO version={} type={} page={}", version, conceptType, pageNumber);
      EscoTaxonomyPage page = client.fetch(conceptType, pageNumber);
      repository.persistPage(releaseId, version, conceptType, page);
      if (page.hasMore()) {
        context.putInt(NEXT_PAGE, pageNumber + 1);
        return RepeatStatus.CONTINUABLE;
      }
      if (conceptType.equals("skill")) {
        context.putString(CONCEPT_TYPE, "occupation");
        context.putInt(NEXT_PAGE, 0);
        return RepeatStatus.CONTINUABLE;
      }
      repository.activate(releaseId);
      log.info("Activated ESCO taxonomy version={}", version);
      return RepeatStatus.FINISHED;
    } catch (RuntimeException failure) {
      repository.fail(releaseId);
      throw failure;
    }
  }
}
