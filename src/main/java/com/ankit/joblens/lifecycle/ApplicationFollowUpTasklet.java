package com.ankit.joblens.lifecycle;

import java.time.LocalDate;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class ApplicationFollowUpTasklet implements Tasklet {

    public static final String GENERATION_VERSION = "follow-up-v1";

    private final ApplicationLifecycleRepository repository;
    private final ApplicationLifecyclePolicy policy;
    private final LocalDate businessDate;
    private final Long failAfterApplications;
    private final boolean firstExecution;

    public ApplicationFollowUpTasklet(ApplicationLifecycleRepository repository,
            ApplicationLifecyclePolicy policy, LocalDate businessDate,
            Long failAfterApplications, boolean firstExecution) {
        this.repository = repository;
        this.policy = policy;
        this.businessDate = businessDate;
        this.failAfterApplications = failAfterApplications;
        this.firstExecution = firstExecution;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long processed = 0;
        for (FollowUpApplication application : repository.findCurrentApplications(businessDate)) {
            contribution.incrementReadCount();
            var plan = policy.followUpFor(application.status());
            if (plan.isPresent()) {
                repository.cancelStaleFollowUps(application.applicationId(), application.historyId());
                repository.upsertFollowUp(application, plan.orElseThrow(), GENERATION_VERSION);
                contribution.incrementWriteCount(1);
            }
            else {
                repository.cancelOpenFollowUps(application.applicationId());
            }
            processed++;
            if (firstExecution && failAfterApplications != null && processed >= failAfterApplications) {
                throw new InjectedFollowUpFailureException();
            }
        }
        return RepeatStatus.FINISHED;
    }
}
