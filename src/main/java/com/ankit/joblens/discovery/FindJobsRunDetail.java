package com.ankit.joblens.discovery;

import java.util.List;

public record FindJobsRunDetail(FindJobsRunSummary run, List<SourceRunSummary> sources) {}
