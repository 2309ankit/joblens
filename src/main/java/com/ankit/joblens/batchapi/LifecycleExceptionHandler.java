package com.ankit.joblens.batchapi;

import java.time.Instant;
import java.util.Map;

import com.ankit.joblens.lifecycle.LifecycleConflictException;
import com.ankit.joblens.lifecycle.LifecycleNotFoundException;
import com.ankit.joblens.lifecycle.LifecycleValidationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LifecycleExceptionHandler {

    @ExceptionHandler(LifecycleNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(LifecycleNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "LIFECYCLE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(LifecycleConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(LifecycleConflictException exception) {
        return error(HttpStatus.CONFLICT, "LIFECYCLE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(LifecycleValidationException.class)
    ResponseEntity<Map<String, Object>> invalid(LifecycleValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_LIFECYCLE_REQUEST", exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", code,
                "message", message));
    }
}
