package com.ankit.joblens.searchprofile;

import tools.jackson.databind.ObjectMapper;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;

@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class SearchProfileImportJobConfiguration {

    static final int CHUNK_SIZE = 2;
    private static final String[] COLUMN_NAMES = {
            "profile_id", "source", "source_key", "keywords", "location",
            "include_skills", "exclude_skills", "employment_type", "active"
    };

    @Bean
    @StepScope
    FlatFileItemReader<SearchProfileCsvRow> searchProfileReader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames(COLUMN_NAMES);
        tokenizer.setStrict(true);

        LineMapper<SearchProfileCsvRow> lineMapper = (line, lineNumber) -> {
            var fields = tokenizer.tokenize(line);
            return new SearchProfileCsvRow(
                    lineNumber,
                    line,
                    fields.readString("profile_id"),
                    fields.readString("source"),
                    fields.readString("source_key"),
                    fields.readString("keywords"),
                    fields.readString("location"),
                    fields.readString("include_skills"),
                    fields.readString("exclude_skills"),
                    fields.readString("employment_type"),
                    fields.readString("active"));
        };
        FlatFileItemReader<SearchProfileCsvRow> reader = new FlatFileItemReader<>(lineMapper);
        reader.setName("searchProfileCsvReader");
        reader.setResource(new FileSystemResource(inputFile));
        reader.setLinesToSkip(1);
        reader.setStrict(true);
        reader.setSaveState(true);
        return reader;
    }

    @Bean
    @StepScope
    SearchProfileProcessor searchProfileProcessor(
            @Value("#{jobParameters['failOnRow']}") Long failOnRow) {
        return new SearchProfileProcessor(failOnRow);
    }

    @Bean
    SearchProfileJdbcWriter searchProfileWriter(JdbcTemplate jdbcTemplate) {
        return new SearchProfileJdbcWriter(jdbcTemplate);
    }

    @Bean
    @StepScope
    SearchProfileRejectionListener searchProfileRejectionListener(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("#{jobParameters['inputFile']}") String inputFile) {
        return new SearchProfileRejectionListener(jdbcTemplate, objectMapper, inputFile);
    }

    @Bean
    Step searchProfileImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<SearchProfileCsvRow> searchProfileReader,
            SearchProfileProcessor searchProfileProcessor,
            SearchProfileJdbcWriter searchProfileWriter,
            SearchProfileRejectionListener searchProfileRejectionListener) {
        return new StepBuilder("searchProfileImportStep", jobRepository)
                .<SearchProfileCsvRow, SearchProfile>chunk(CHUNK_SIZE, transactionManager)
                .reader(searchProfileReader)
                .processor(searchProfileProcessor)
                .writer(searchProfileWriter)
                .faultTolerant()
                .skip(SearchProfileValidationException.class)
                .skip(org.springframework.batch.infrastructure.item.file.FlatFileParseException.class)
                .skipLimit(100)
                .listener((org.springframework.batch.core.listener.SkipListener<SearchProfileCsvRow, SearchProfile>)
                        searchProfileRejectionListener)
                .listener((org.springframework.batch.core.listener.StepExecutionListener) searchProfileRejectionListener)
                .build();
    }

    @Bean
    Job searchProfileImportJob(JobRepository jobRepository, Step searchProfileImportStep) {
        return new JobBuilder("searchProfileImportJob", jobRepository)
                .start(searchProfileImportStep)
                .build();
    }
}
