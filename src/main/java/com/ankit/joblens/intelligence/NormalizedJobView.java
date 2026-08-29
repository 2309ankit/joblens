package com.ankit.joblens.intelligence;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record NormalizedJobView(long id, String source, String externalJobId, String title, String company,
        String location, String descriptionText, String employmentType, BigDecimal salaryMin, BigDecimal salaryMax,
        String salaryCurrency, String remoteType, OffsetDateTime postedAt, String sourceUrl, String contentHash) {}
