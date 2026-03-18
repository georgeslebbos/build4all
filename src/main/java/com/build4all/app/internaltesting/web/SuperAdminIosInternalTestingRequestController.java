package com.build4all.app.internaltesting.web;

import com.build4all.app.internaltesting.dto.IosInternalTestingRequestResponseDto;
import com.build4all.app.internaltesting.service.IosInternalTestingRequestService;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
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
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean manualOnly
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            List<IosInternalTestingRequestResponseDto> requests =
                    service.listAllForSuperAdmin(requesterAdminId, status, manualOnly);

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

    @GetMapping("/{requestId}")
    public ResponseEntity<?> getRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            IosInternalTestingRequestResponseDto request =
                    service.getRequestForSuperAdmin(requesterAdminId, requestId);

            return ResponseEntity.ok(Map.of(
                    "message", "iOS internal testing request fetched successfully",
                    "request", request
            ));

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

            return ResponseEntity.ok(Map.of(
                    "message", "iOS internal testing request processed successfully",
                    "request", request
            ));

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

            return ResponseEntity.ok(Map.of(
                    "message", "iOS internal testing request synced successfully",
                    "request", request
            ));

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

    @PostMapping("/sync-all")
    public ResponseEntity<?> syncAllRequests(
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            int updated = service.syncAllForSuperAdmin(requesterAdminId);

            return ResponseEntity.ok(Map.of(
                    "message", "iOS internal testing sync finished successfully",
                    "updatedCount", updated
            ));

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

    @PostMapping("/{requestId}/mark-manual-review")
    public ResponseEntity<?> markManualReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            String note = body != null ? body.get("note") : null;

            IosInternalTestingRequestResponseDto request =
                    service.markManualReviewRequired(requesterAdminId, requestId, note);

            return ResponseEntity.ok(Map.of(
                    "message", "Request marked for manual review successfully",
                    "request", request
            ));

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

    @PostMapping("/{requestId}/mark-ready-manual")
    public ResponseEntity<?> markReadyManual(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            String appleUserId = body != null ? body.get("appleUserId") : null;
            String note = body != null ? body.get("note") : null;

            IosInternalTestingRequestResponseDto request =
                    service.markReadyManually(requesterAdminId, requestId, appleUserId, note);

            return ResponseEntity.ok(Map.of(
                    "message", "Request marked READY manually successfully",
                    "request", request
            ));

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

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<?> cancelRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        try {
            Long requesterAdminId = requireSuperAdminAndGetAdminId(authHeader);

            String note = body != null ? body.get("note") : null;

            IosInternalTestingRequestResponseDto request =
                    service.cancelRequestForSuperAdmin(requesterAdminId, requestId, note);

            return ResponseEntity.ok(Map.of(
                    "message", "Request cancelled successfully",
                    "request", request
            ));

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        String role = jwtUtil.extractRole(token);
        if (role == null || !role.equalsIgnoreCase("SUPER_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: SUPER_ADMIN only");
        }

        Long adminId = jwtUtil.extractAdminId(token);
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing admin id in token");
        }

        return adminId;
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid Authorization header");
        }
        return authHeader.substring(7).trim();
    }
}