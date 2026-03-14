package com.build4all.app.internaltesting.web;

import com.build4all.app.internaltesting.dto.IosInternalTestingRequestResponseDto;
import com.build4all.app.internaltesting.service.IosInternalTestingRequestService;
import com.build4all.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/ios-internal-requests")
public class SuperAdminIosInternalTestingRequestController {

    private final IosInternalTestingRequestService service;
    private final JwtUtil jwtUtil;

    public SuperAdminIosInternalTestingRequestController(
            IosInternalTestingRequestService service,
            JwtUtil jwtUtil
    ) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<?> listRequests(
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            List<IosInternalTestingRequestResponseDto> requests =
                    service.listAllForSuperAdmin(requesterAdminId);

            Map<String, Object> body = new HashMap<>();
            body.put("message", "iOS internal testing requests fetched successfully");
            body.put("requests", requests);

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

    @PostMapping("/{requestId}/process")
    public ResponseEntity<?> processRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            IosInternalTestingRequestResponseDto request =
                    service.processRequest(requesterAdminId, requestId);

            Map<String, Object> body = new HashMap<>();
            body.put("message", "iOS internal testing request processed successfully");
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

    @PostMapping("/{requestId}/sync")
    public ResponseEntity<?> syncRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            IosInternalTestingRequestResponseDto request =
                    service.syncSingleRequest(requesterAdminId, requestId);

            Map<String, Object> body = new HashMap<>();
            body.put("message", "iOS internal testing request synced successfully");
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

    private Long requireSuperAdminAndGetAdminId(String authHeader) {
        String token = extractToken(authHeader);

        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid token"
            );
        }

        String role = jwtUtil.extractRole(token);
        if (role == null || !role.equalsIgnoreCase("SUPER_ADMIN")) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Forbidden: SUPER_ADMIN only"
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