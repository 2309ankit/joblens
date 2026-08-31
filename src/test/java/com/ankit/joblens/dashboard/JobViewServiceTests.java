package com.ankit.joblens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobViewServiceTests {
  @Test
  void resolvesExistingAdzunaJobsAgainstTheirConfiguredMarket() {
    JobViewRepository repository = mock(JobViewRepository.class);
    UUID workspaceId = UUID.randomUUID();
    when(repository.findOpenTarget(42, workspaceId))
        .thenReturn(
            new JobViewRepository.OpenTarget(
                "https://www.adzuna.co.uk/details/123?utm_source=api", "ADZUNA", "in"));

    URI target = new JobViewService(repository).recordAndResolve(42, 7, workspaceId);

    assertThat(target).isEqualTo(URI.create("https://www.adzuna.in/details/123"));
    verify(repository).record(42, 7);
  }
}
