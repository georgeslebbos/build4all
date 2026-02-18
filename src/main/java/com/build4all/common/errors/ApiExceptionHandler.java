package com.build4all.common.errors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private Map<String, Object> body(HttpStatus status, String code, String message, String path, Map<String, Object> details, String requestId) {
        Map<String, Object> b = new HashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status", status.value());
        b.put("error", message);
        b.put("code", code);
        b.put("path", path);
        b.put("requestId", requestId);
        if (details != null && !details.isEmpty()) b.put("details", details);
        return b;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApi(ApiException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(ex.getStatus()).body(
                body(ex.getStatus(), ex.getCode(), ex.getMessage(), req.getRequestURI(), ex.getDetails(), requestId)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleConflict(IllegalStateException ex, HttpServletRequest req) {
        // If you still throw IllegalState for business conflicts, map it to 409
        String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                body(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> details = Map.of("parameter", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                body(HttpStatus.BAD_REQUEST, "MISSING_PARAM", ex.getMessage(), req.getRequestURI(), details, requestId)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleBadJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                body(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Invalid JSON body", req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleSql(DataIntegrityViolationException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                body(HttpStatus.CONFLICT, "DATA_CONFLICT", "Data constraint violation", req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnknown(Exception ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        // Don't leak internal exception messages to users in prod.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "Unexpected server error. Use requestId for support.", req.getRequestURI(), null, requestId)
        );
    }
}
