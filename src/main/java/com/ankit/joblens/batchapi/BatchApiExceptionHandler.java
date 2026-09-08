package com.ankit.joblens.batchapi;

import com.ankit.joblens.discovery.ActiveFindJobsRunException;
import com.ankit.joblens.discovery.StaleFindJobsRunException;
import com.ankit.joblens.workspace.WorkspaceNotReadyException;
import java.time.Instant;
import java.util.Map;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BatchApiExceptionHandler {

  @ExceptionHandler(WorkspaceNotReadyException.class)
  ResponseEntity<Map<String, Object>> workspaceNotReady(WorkspaceNotReadyException exception) {
    return error(HttpStatus.CONFLICT, "WORKSPACE_NOT_READY", exception.getMessage());
  }

  @ExceptionHandler(JobInstanceAlreadyCompleteException.class)
  ResponseEntity<Map<String, Object>> alreadyComplete(
      JobInstanceAlreadyCompleteException exception) {
    return error(
        HttpStatus.CONFLICT,
        "JOB_ALREADY_COMPLETE",
        "This operation has already completed. Choose new input before running it again.");
  }

  @ExceptionHandler({JobExecutionAlreadyRunningException.class, JobRestartException.class})
  ResponseEntity<Map<String, Object>> launchConflict(Exception exception) {
    return error(
        HttpStatus.CONFLICT,
        "JOB_ACTIVE",
        "An operation is already in progress. Check its status and try again when it finishes.");
  }

  @ExceptionHandler(ActiveFindJobsRunException.class)
  ResponseEntity<Map<String, Object>> activeFindJobsRun(ActiveFindJobsRunException exception) {
    return error(
        HttpStatus.CONFLICT,
        "JOB_ACTIVE",
        "A Find Jobs run is already in progress. Check its status before trying again.");
  }

  @ExceptionHandler(StaleFindJobsRunException.class)
  ResponseEntity<Map<String, Object>> staleFindJobsRun(StaleFindJobsRunException exception) {
    return error(
        HttpStatus.CONFLICT,
        "JOB_STALE",
        "The previous search stopped updating. Restart it to resume safely.");
  }

  @ExceptionHandler(InvalidJobParametersException.class)
  ResponseEntity<Map<String, Object>> invalidParameters(InvalidJobParametersException exception) {
    return error(
        HttpStatus.BAD_REQUEST, "INVALID_JOB_PARAMETERS", "The search request is invalid.");
  }

  @ExceptionHandler(JobExecutionException.class)
  ResponseEntity<Map<String, Object>> launchFailure(JobExecutionException exception) {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "JOB_UNAVAILABLE",
        "The operation could not start. Please try again shortly.");
  }

  private static ResponseEntity<Map<String, Object>> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "timestamp",
                Instant.now().toString(),
                "status",
                status.value(),
                "error",
                code,
                "message",
                message == null ? "Batch request failed" : message));
  }
}
