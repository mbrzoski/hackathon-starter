package com.hackathonstarter.common;

import com.hackathonstarter.llm.LlmException;
import com.hackathonstarter.llm.structured.StructuredOutputException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request body", req, List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(StructuredOutputException.class)
    ResponseEntity<ApiError> structured(StructuredOutputException ex, HttpServletRequest req) {
        log.warn("Structured output failed: {}", ex.getErrors());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, ex.getErrors());
    }

    @ExceptionHandler(LlmException.class)
    ResponseEntity<ApiError> llm(LlmException ex, HttpServletRequest req) {
        log.warn("LLM call failed ({}): {}", ex.getStatus(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", req, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req, List<String> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
