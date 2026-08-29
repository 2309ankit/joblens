package com.ankit.joblens.intelligence;

import com.ankit.joblens.discovery.JobSource;

public interface JobPostingNormalizer {

    boolean supports(JobSource source);

    NormalizedJob normalize(RawJobPosting rawJobPosting);
}
