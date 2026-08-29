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
    @StepScope
    NormalizedJobViewReader skillExtractionReader(JdbcTemplate jdbcTemplate) {
        return new NormalizedJobViewReader(jdbcTemplate, true);
    }

    @Bean
    @StepScope
    SkillExtractionProcessor skillExtractionProcessor(SkillExtractor extractor) {
        return new SkillExtractionProcessor(extractor);
    }

    @Bean
    SkillExtractionWriter skillExtractionWriter(JdbcTemplate jdbcTemplate) {
        return new SkillExtractionWriter(jdbcTemplate);
    }

    @Bean
    @StepScope
    NormalizedJobViewReader scoringReader(JdbcTemplate jdbcTemplate) {
        return new NormalizedJobViewReader(jdbcTemplate, false);
    }

    @Bean
    @StepScope
    ScoringProcessor scoringProcessor(JobScoreCalculator calculator) {
        return new ScoringProcessor(calculator);
    }

    @Bean
    ScoringWriter scoringWriter(JdbcTemplate jdbcTemplate) {
        return new ScoringWriter(jdbcTemplate);
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
    Step skillExtractionStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            NormalizedJobViewReader skillExtractionReader, SkillExtractionProcessor skillExtractionProcessor,
            SkillExtractionWriter skillExtractionWriter, @Value("${joblens.intelligence.chunk-size:20}") int chunkSize) {
        return new StepBuilder("skillExtractionStep", jobRepository)
                .<NormalizedJobView, ExtractedJobSkills>chunk(chunkSize).transactionManager(transactionManager)
                .reader(skillExtractionReader).processor(skillExtractionProcessor).writer(skillExtractionWriter).build();
    }

    @Bean
    Step scoringStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            NormalizedJobViewReader scoringReader, ScoringProcessor scoringProcessor,
            ScoringWriter scoringWriter, @Value("${joblens.intelligence.chunk-size:20}") int chunkSize) {
        return new StepBuilder("scoringStep", jobRepository)
                .<NormalizedJobView, JobScore>chunk(chunkSize).transactionManager(transactionManager)
                .reader(scoringReader).processor(scoringProcessor).writer(scoringWriter).build();
    }

    @Bean
    Job jobIntelligenceJob(JobRepository jobRepository, Step jobNormalizationStep,
            Step skillExtractionStep, Step scoringStep) {
        return new JobBuilder("jobIntelligenceJob", jobRepository)
                .start(jobNormalizationStep)
                .next(skillExtractionStep)
                .next(scoringStep)
                .build();
    }
}
