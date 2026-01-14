package com.build4all.publish.web;

import com.build4all.publish.domain.AppPublishRequest;
import com.build4all.publish.domain.PublishStatus;
import com.build4all.publish.dto.AdminDecisionDto;
import com.build4all.publish.dto.AppPublishAdminMapper;
import com.build4all.publish.service.AppPublishService;
import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public ResponseEntity<?> listByStatus(
            HttpServletRequest request,
            @RequestParam(defaultValue = "SUBMITTED") PublishStatus status
    ) {
        String token = jwtUtil.extractTokenFromRequest(request);

        if (!jwtUtil.isSuperAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only SUPER_ADMIN allowed"));
        }

        var list = publishService.listByStatusForAdmin(status);
        return ResponseEntity.ok(Map.of("message", "OK", "data", list));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<?> approve(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) AdminDecisionDto dto
    ) {
        String token = jwtUtil.extractTokenFromRequest(request);

        if (!jwtUtil.isSuperAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only SUPER_ADMIN allowed"));
        }

        Long adminId = jwtUtil.extractId(token);
        String notes = (dto != null) ? dto.getNotes() : null;

        AppPublishRequest out = publishService.approve(requestId, adminId, notes);

        return ResponseEntity.ok(Map.of(
                "message", "Approved",
                "data", AppPublishAdminMapper.toDto(out) // ✅ DTO not entity
        ));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) AdminDecisionDto dto
    ) {
        String token = jwtUtil.extractTokenFromRequest(request);

        if (!jwtUtil.isSuperAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only SUPER_ADMIN allowed"));
        }

        Long adminId = jwtUtil.extractId(token);
        String notes = (dto != null) ? dto.getNotes() : null;

        AppPublishRequest out = publishService.reject(requestId, adminId, notes);

        return ResponseEntity.ok(Map.of(
                "message", "Rejected",
                "data", AppPublishAdminMapper.toDto(out) // ✅ DTO not entity
        ));
    }
}
