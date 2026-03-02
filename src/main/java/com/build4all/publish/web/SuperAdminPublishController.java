package com.build4all.publish.web;

import com.build4all.publish.domain.AppPublishRequest;
import com.build4all.publish.domain.PublishStatus;
import com.build4all.publish.dto.AdminDecisionDto;
import com.build4all.publish.dto.AppPublishAdminMapper;
import com.build4all.publish.service.AppPublishService;
import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/publish")
public class SuperAdminPublishController {

    private final AppPublishService publishService;
    private final JwtUtil jwtUtil;

    public SuperAdminPublishController(AppPublishService publishService, JwtUtil jwtUtil) {
        this.publishService = publishService;
        this.jwtUtil = jwtUtil;
    }

    private String token(HttpServletRequest request) {
        return jwtUtil.extractTokenFromRequest(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> listByStatus(
            HttpServletRequest request,
            @RequestParam(defaultValue = "SUBMITTED") PublishStatus status
    ) {
        var list = publishService.listByStatusForAdmin(status);
        return ResponseEntity.ok(Map.of("message", "OK", "data", list));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> approve(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) AdminDecisionDto dto
    ) {
        Long adminId = jwtUtil.extractId(token(request));
        String notes = (dto != null) ? dto.getNotes() : null;

        AppPublishRequest out = publishService.approve(requestId, adminId, notes);

        return ResponseEntity.ok(Map.of(
                "message", "Approved",
                "data", AppPublishAdminMapper.toDto(out)
        ));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> reject(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) AdminDecisionDto dto
    ) {
        Long adminId = jwtUtil.extractId(token(request));
        String notes = (dto != null) ? dto.getNotes() : null;

        AppPublishRequest out = publishService.reject(requestId, adminId, notes);

        return ResponseEntity.ok(Map.of(
                "message", "Rejected",
                "data", AppPublishAdminMapper.toDto(out)
        ));
    }

    // ✅ Optional: consistent error payloads (recommended)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<?> notFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
    }
}