package com.ankit.joblens.intelligence;

import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class JobIntelligenceConfiguration {

    @Bean
    @StepScope
    RawJobPostingReader rawJobPostingReader(JdbcTemplate jdbcTemplate) {
        return new RawJobPostingReader(jdbcTemplate);
    }

    @Bean
    @StepScope
    JobNormalizationProcessor jobNormalizationProcessor(
            List<JobPostingNormalizer> normalizers,
            JobRepository jobRepository,
            @Value("#{jobParameters['failAfterItems']}") Long failAfterItems,
            @Value("#{stepExecution}") StepExecution stepExecution) {
        var jobExecution = stepExecution.getJobExecution();
        boolean firstExecution = jobRepository.getJobExecutions(jobExecution.getJobInstance()).size() == 1;
        return new JobNormalizationProcessor(normalizers, failAfterItems, firstExecution);
    }

    @Bean
    NormalizedJobWriter normalizedJobWriter(JdbcTemplate jdbcTemplate) {
        return new NormalizedJobWriter(jdbcTemplate);
    }

    @Bean
    NormalizationFailureListener normalizationFailureListener(NormalizationStatusService statusService) {
        return new NormalizationFailureListener(statusService);
    }

    @Bean
    Step jobNormalizationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RawJobPostingReader rawJobPostingReader,
            JobNormalizationProcessor jobNormalizationProcessor,
            NormalizedJobWriter normalizedJobWriter,
            NormalizationFailureListener normalizationFailureListener,
            @Value("${joblens.intelligence.chunk-size:20}") int chunkSize) {
        return new StepBuilder("jobNormalizationStep", jobRepository)
                .<RawJobPosting, NormalizedJob>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(rawJobPostingReader)
                .processor(jobNormalizationProcessor)
                .writer(normalizedJobWriter)
                .faultTolerant()
                .skip(NormalizationRejectedException.class)
                .skipLimit(100)
                .listener((org.springframework.batch.core.listener.ItemProcessListener<RawJobPosting, NormalizedJob>)
                        normalizationFailureListener)
                .listener((org.springframework.batch.core.listener.SkipListener<RawJobPosting, NormalizedJob>)
                        normalizationFailureListener)
                .build();
    }

    @Bean
    Job jobIntelligenceJob(JobRepository jobRepository, Step jobNormalizationStep) {
        return new JobBuilder("jobIntelligenceJob", jobRepository)
                .start(jobNormalizationStep)
                .build();
    }
}
