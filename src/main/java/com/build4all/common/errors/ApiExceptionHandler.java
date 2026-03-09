package com.build4all.common.errors;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private Map<String, Object> body(HttpStatus status, String code, String message,
                                     String path, Map<String, Object> details, String requestId) {
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

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleUnauthorized(AuthenticationException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} auth error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Unauthorized - missing or invalid token", req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} forbidden error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                body(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Forbidden - access denied", req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApi(ApiException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} api error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(ex.getStatus()).body(
                body(ex.getStatus(), ex.getCode(), ex.getMessage(),
                        req.getRequestURI(), ex.getDetails(), requestId)
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        log.warn("requestId={} path={} response status error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(status).body(
                body(status, "HTTP_" + status.value(),
                        ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                        req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} bad request error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                body(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                        ex.getMessage(), req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleConflict(IllegalStateException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} conflict error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                body(HttpStatus.CONFLICT, "CONFLICT",
                        ex.getMessage(), req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} missing param error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        Map<String, Object> details = Map.of("parameter", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                body(HttpStatus.BAD_REQUEST, "MISSING_PARAM",
                        ex.getMessage(), req.getRequestURI(), details, requestId)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleBadJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} invalid json error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                body(HttpStatus.BAD_REQUEST, "INVALID_JSON",
                        "Invalid JSON body", req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleSql(DataIntegrityViolationException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.error("requestId={} path={} data conflict error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                body(HttpStatus.CONFLICT, "DATA_CONFLICT",
                        "Data constraint violation", req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNotFound(NoSuchElementException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} not found error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                body(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        ex.getMessage(), req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> handleSecurity(SecurityException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} security error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                body(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        ex.getMessage(), req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();
        log.warn("requestId={} path={} no handler error={}", requestId, req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                body(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "No endpoint for this path", req.getRequestURI(), null, requestId)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnknown(Exception ex, HttpServletRequest req) {
        String requestId = UUID.randomUUID().toString();

        ex.printStackTrace(); // مهم جدًا مؤقتًا

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "Unexpected server error. Use requestId for support.",
                        req.getRequestURI(), null, requestId)
        );
    }
}