package com.ankit.joblens.batchapi;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ResumeProfileController.class)
public class CandidateProfileExceptionHandler {

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  ResponseEntity<Map<String, Object>> invalid(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_REQUEST", message(exception));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<Map<String, Object>> conflict(IllegalStateException exception) {
    return error(HttpStatus.CONFLICT, "PROFILE_NOT_READY", exception.getMessage());
  }

  private static String message(Exception exception) {
    if (exception instanceof MethodArgumentNotValidException validationException) {
      return validationException.getBindingResult().getAllErrors().stream()
          .findFirst()
          .map(error -> error.getDefaultMessage())
          .orElse("Profile request is invalid");
    }
    return exception.getMessage();
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
                message == null ? "Profile request failed" : message));
  }
}
