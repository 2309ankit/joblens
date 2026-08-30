package com.ankit.joblens.intelligence;

import com.ankit.joblens.discovery.JobSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.batch.infrastructure.item.ItemProcessor;

public class JobNormalizationProcessor implements ItemProcessor<RawJobPosting, NormalizedJob> {

  private final List<JobPostingNormalizer> normalizers;
  private final Long failAfterItems;
  private final boolean failureInjectionEnabled;
  private final AtomicLong processed = new AtomicLong();

  public JobNormalizationProcessor(
      List<JobPostingNormalizer> normalizers,
      Long failAfterItems,
      boolean failureInjectionEnabled) {
    this.normalizers = normalizers;
    this.failAfterItems = failAfterItems;
    this.failureInjectionEnabled = failureInjectionEnabled;
  }

  @Override
  public NormalizedJob process(RawJobPosting raw) {
    if (failureInjectionEnabled
        && failAfterItems != null
        && processed.incrementAndGet() == failAfterItems) {
      throw new InjectedNormalizationFailureException(raw.id());
    }
    JobSource source;
    try {
      source = JobSource.valueOf(raw.source());
    } catch (IllegalArgumentException exception) {
      throw new NormalizationRejectedException(
          raw, "Unsupported raw job source: " + raw.source(), exception);
    }
    JobPostingNormalizer normalizer =
        normalizers.stream()
            .filter(candidate -> candidate.supports(source))
            .findFirst()
            .orElseThrow(
                () ->
                    new NormalizationRejectedException(
                        raw, "No job posting normalizer supports source " + source));
    return normalizer.normalize(raw);
  }
}
