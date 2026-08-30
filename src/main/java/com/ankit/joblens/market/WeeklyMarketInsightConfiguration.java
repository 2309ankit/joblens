package com.ankit.joblens.market;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class WeeklyMarketInsightConfiguration {
 @Bean WeeklyMarketInsightRepository weeklyMarketInsightRepository(org.springframework.jdbc.core.JdbcTemplate jdbc){return new WeeklyMarketInsightRepository(jdbc);}
 @Bean @StepScope Tasklet weeklyMarketInsightTasklet(WeeklyMarketInsightRepository repo,@Value("#{jobParameters['weekStart']}") LocalDate weekStart){
   return (c,ctx)->{ repo.generate(weekStart); return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;};
 }
 @Bean Step weeklyMarketInsightStep(JobRepository r, PlatformTransactionManager tx, Tasklet weeklyMarketInsightTasklet){
   return new StepBuilder("weeklyMarketInsightStep",r).tasklet(weeklyMarketInsightTasklet,tx).build();
 }
 @Bean Job weeklyMarketInsightJob(JobRepository r, Step weeklyMarketInsightStep){return new JobBuilder("weeklyMarketInsightJob",r).start(weeklyMarketInsightStep).build();}
}
