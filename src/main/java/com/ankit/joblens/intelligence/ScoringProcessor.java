package com.ankit.joblens.intelligence;
import org.springframework.batch.infrastructure.item.ItemProcessor;
public class ScoringProcessor implements ItemProcessor<NormalizedJobView,JobScore>{private final JobScoreCalculator c; public ScoringProcessor(JobScoreCalculator c){this.c=c;} public JobScore process(NormalizedJobView j){return c.calculate(j);}}
