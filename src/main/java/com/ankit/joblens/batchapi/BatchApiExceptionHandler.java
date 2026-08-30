package com.ankit.joblens.batchapi;

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
    return error(HttpStatus.CONFLICT, "JOB_INSTANCE_ALREADY_COMPLETE", exception.getMessage());
  }

  @ExceptionHandler({JobExecutionAlreadyRunningException.class, JobRestartException.class})
  ResponseEntity<Map<String, Object>> launchConflict(Exception exception) {
    return error(HttpStatus.CONFLICT, "JOB_LAUNCH_CONFLICT", exception.getMessage());
  }

  @ExceptionHandler(InvalidJobParametersException.class)
  ResponseEntity<Map<String, Object>> invalidParameters(InvalidJobParametersException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_JOB_PARAMETERS", exception.getMessage());
  }

  @ExceptionHandler(JobExecutionException.class)
  ResponseEntity<Map<String, Object>> launchFailure(JobExecutionException exception) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "JOB_LAUNCH_FAILED", exception.getMessage());
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
