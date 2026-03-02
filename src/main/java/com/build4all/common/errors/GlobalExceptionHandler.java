package com.build4all.common.errors;

import com.build4all.common.api.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApi(ApiException e) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        try {
            var m = e.getClass().getMethod("getStatus");
            Object s = m.invoke(e);
            if (s instanceof HttpStatus hs) status = hs;
        } catch (Exception ignore) {}

        return ResponseEntity.status(status)
                .body(ApiResponse.err(e.getMessage(), "API_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.err("Server error", "INTERNAL"));
    }
}