package com.ankit.joblens.batchapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.ankit.joblens.discovery.ActiveFindJobsRunException;
import com.ankit.joblens.discovery.StaleFindJobsRunException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;

class BatchApiExceptionHandlerTests {
  private final BatchApiExceptionHandler handler = new BatchApiExceptionHandler();

  @Test
  void replacesBatchLaunchDetailsWithAStableProductError() {
    var response =
        handler.launchConflict(
            new JobExecutionAlreadyRunningException(
                "JobInstance: id=36, version=0, Job=[findJobsJob]"));

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody())
        .containsEntry("error", "JOB_ACTIVE")
        .containsEntry(
            "message",
            "An operation is already in progress. Check its status and try again when it finishes.");
    assertSafe(response.getBody());
  }

  @Test
  void reportsAStaleSearchAsRecoverableWithoutBatchDetails() {
    var response = handler.staleFindJobsRun(new StaleFindJobsRunException());

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody())
        .containsEntry("error", "JOB_STALE")
        .containsEntry(
            "message", "The previous search stopped updating. Restart it to resume safely.");
    assertSafe(response.getBody());
  }

  @Test
  void reportsAnAdmissionConflictWithoutFrameworkDetails() {
    var response = handler.activeFindJobsRun(new ActiveFindJobsRunException());

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody())
        .containsEntry("error", "JOB_ACTIVE")
        .containsEntry(
            "message",
            "A Find Jobs run is already in progress. Check its status before trying again.");
    assertSafe(response.getBody());
  }

  private static void assertSafe(Map<String, Object> body) {
    assertThat(body.toString())
        .doesNotContain("JobInstance")
        .doesNotContain("JobExecution")
        .doesNotContain("findJobsJob")
        .doesNotContain("version=0");
  }
}
