package com.build4all.app.internaltesting.web;

import com.build4all.app.internaltesting.dto.CreateIosInternalTestingRequestDto;
import com.build4all.app.internaltesting.dto.IosInternalTestingRequestResponseDto;
import com.build4all.app.internaltesting.service.IosInternalTestingRequestService;
import com.build4all.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/apps")
@PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
public class OwnerIosInternalTestingRequestController {

    private final IosInternalTestingRequestService service;
    private final JwtUtil jwtUtil;

    public OwnerIosInternalTestingRequestController(
            IosInternalTestingRequestService service,
            JwtUtil jwtUtil
    ) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/{linkId}/ios-internal-requests")
    public ResponseEntity<?> createRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @Valid @RequestBody CreateIosInternalTestingRequestDto dto
    ) {
        try {
            Long requesterAdminId = adminIdFromToken(authHeader);

            IosInternalTestingRequestResponseDto created =
                    service.createRequest(requesterAdminId, linkId, dto);

            Map<String, Object> body = new HashMap<>();
            body.put("message", "iOS internal testing request created successfully");
            body.put("request", created);

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    @GetMapping("/{linkId}/ios-internal-requests/latest")
    public ResponseEntity<?> getLatestRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId
    ) {
        try {
            Long requesterAdminId = adminIdFromToken(authHeader);

            IosInternalTestingRequestResponseDto request =
                    service.getLatestRequest(requesterAdminId, linkId);

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Latest iOS internal testing request fetched successfully");
            body.put("request", request);

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    private Long adminIdFromToken(String authHeader) {
        String token = extractToken(authHeader);

        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid token"
            );
        }

        String role = jwtUtil.extractRole(token);
        if (role == null || (!role.equalsIgnoreCase("OWNER") && !role.equalsIgnoreCase("SUPER_ADMIN"))) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Forbidden"
            );
        }

        Long adminId = jwtUtil.extractAdminId(token);
        if (adminId == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Missing admin id in token"
            );
        }

        return adminId;
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Missing/invalid Authorization header"
            );
        }
        return authHeader.substring(7).trim();
    }
}