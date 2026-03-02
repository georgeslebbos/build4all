package com.build4all.admin.web;

import com.build4all.admin.dto.AdminAppAssignmentRequest;
import com.build4all.admin.dto.AdminAppAssignmentResponse;
import com.build4all.admin.service.AdminUserProjectService;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminUserProjectsController (SECURED)
 *
 * Fix applied:
 * ✅ We do NOT accept adminId from URL anymore (prevents IDOR/BOLA).
 * ✅ We extract adminId from Authorization Bearer token.
 *
 * Who can access:
 * - OWNER / ADMIN / SUPER_ADMIN (whatever you treat as "admin-side roles")
 *
 * Routes:
 * - /api/admin-users/apps
 * - /api/admin-users/projects/{projectId}/apps
 * - /api/admin-users/projects/{projectId}/apps/{slug}
 * - /api/admin-users/projects/{projectId}/apps/{slug}/logo
 * - /api/admin-users/projects/{projectId}/apps/{slug}/artifact
 */
@RestController
@RequestMapping("/api/admin-users")
public class AdminUserProjectsController {

    private final AdminUserProjectService service;
    private final JwtUtil jwtUtil;

    public AdminUserProjectsController(AdminUserProjectService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    /* =====================================================
       Response helpers (consistent JSON errors)
       ===================================================== */

    private ResponseEntity<Map<String, Object>> err(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String msg) {
        return err(HttpStatus.UNAUTHORIZED, msg);
    }

    private ResponseEntity<Map<String, Object>> forbidden(String msg) {
        return err(HttpStatus.FORBIDDEN, msg);
    }

    /* =====================================================
       Auth helpers
       ===================================================== */

    /**
     * Extract raw JWT from "Authorization: Bearer <token>"
     */
    private String requireBearer(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing Authorization header");
        }
        return authHeader.substring(7).trim();
    }

    /**
     * Extract adminId from token and enforce admin-side access.
     *
     * NOTE:
     * - This assumes your JwtUtil puts "id" (admin id) in admin tokens.
     * - If you use a different claim, update JwtUtil.extractAdminId(token).
     */
    private Long adminIdFromTokenOrThrow(String authHeader) {
        String token = requireBearer(authHeader);

        // optional but strongly recommended
        try {
            if (!jwtUtil.validateToken(token)) {
                throw new IllegalArgumentException("Invalid or expired token");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        // Only allow admin-side tokens (ADMIN/OWNER/SUPER_ADMIN)
        if (!jwtUtil.isAdminOrOwner(token)) {
            throw new SecurityException("Forbidden");
        }

        Long adminId = jwtUtil.extractAdminId(token);
        if (adminId == null || adminId <= 0) {
            throw new IllegalArgumentException("Token missing admin id claim");
        }

        return adminId;
    }

    /* =====================================================
       APP-AWARE ENDPOINTS (NEW)
       ===================================================== */

    /** List all apps (rows) for the current admin (adminId from token). */
    @GetMapping("/apps")
    public ResponseEntity<?> listApps(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);
            return ResponseEntity.ok(service.list(adminId));

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load apps");
        }
    }

    /** Create or overwrite a single app under a project (adminId from token). */
    @PostMapping("/projects/{projectId}/apps")
    public ResponseEntity<?> assignApp(
            @PathVariable Long projectId,
            @RequestBody AdminAppAssignmentRequest req,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            // Enforce projectId from URL to avoid spoofing
            req.setProjectId(projectId);

            service.assign(adminId, req);
            return ResponseEntity.noContent().build();

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to assign app");
        }
    }

    /** Update only license/validity for an existing app (adminId from token). */
    @PutMapping("/projects/{projectId}/apps/{slug}")
    public ResponseEntity<?> updateAppLicense(
            @PathVariable Long projectId,
            @PathVariable String slug,
            @RequestBody AdminAppAssignmentRequest req,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            service.updateLicense(adminId, projectId, slug, req);
            return ResponseEntity.noContent().build();

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update app license");
        }
    }

    /** Delete one app under admin+project by slug (adminId from token). */
    @DeleteMapping("/projects/{projectId}/apps/{slug}")
    public ResponseEntity<?> removeApp(
            @PathVariable Long projectId,
            @PathVariable String slug,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            service.remove(adminId, projectId, slug);
            return ResponseEntity.noContent().build();

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to remove app");
        }
    }

    /**
     * Upload logo for an app (adminId from token).
     * POST /api/admin-users/projects/{projectId}/apps/{slug}/logo
     */
    @PostMapping(value = "/projects/{projectId}/apps/{slug}/logo", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadLogo(
            @PathVariable Long projectId,
            @PathVariable String slug,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            String url = service.updateAppLogo(adminId, projectId, slug, file);
            var link = service.get(adminId, projectId, slug);

            Map<String, Object> body = new HashMap<>();
            body.put("logoUrl", url);
            body.put("apkUrl", link.getApkUrl() == null ? "" : link.getApkUrl());

            return ResponseEntity.ok(body);

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload logo");
        }
    }

    /** One-shot artifact getter (adminId from token). */
    @GetMapping("/projects/{projectId}/apps/{slug}/artifact")
    public ResponseEntity<?> artifact(
            @PathVariable Long projectId,
            @PathVariable String slug,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            var l = service.get(adminId, projectId, slug);

            return ResponseEntity.ok(new AdminAppAssignmentResponse(
                    l.getProject().getId(),
                    l.getProject().getProjectName(),
                    l.getAppName() == null ? "" : l.getAppName(),
                    l.getSlug(),
                    l.getStatus() == null ? "" : l.getStatus(),
                    l.getLicenseId() == null ? "" : l.getLicenseId(),
                    l.getValidFrom(),
                    l.getEndTo(),
                    l.getThemeId(),
                    l.getApkUrl() == null ? "" : l.getApkUrl(),
                    l.getIpaUrl() == null ? "" : l.getIpaUrl(),
                    l.getBundleUrl() == null ? "" : l.getBundleUrl(),
                    l.getLogoUrl() == null ? "" : l.getLogoUrl(),
                    l.getCurrency() != null ? l.getCurrency().getCode() : null,
                    l.getCurrency() != null ? l.getCurrency().getSymbol() : null
            ));

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load artifact");
        }
    }

    /* =====================================================
       BACKWARD-COMPAT SHIMS (optional)
       Important: legacy endpoints should also use token adminId.
       ===================================================== */

    @Deprecated
    @GetMapping("/projects")
    public ResponseEntity<?> legacyList(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);
            return ResponseEntity.ok(service.list(adminId));
        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load apps");
        }
    }

    @Deprecated
    @PostMapping("/projects")
    public ResponseEntity<?> legacyAssign(
            @RequestBody AdminAppAssignmentRequest req,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            if (req.getProjectId() == null) {
                return err(HttpStatus.BAD_REQUEST, "projectId is required");
            }

            service.assign(adminId, req);
            return ResponseEntity.noContent().build();

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to assign app");
        }
    }

    @Deprecated
    @PutMapping("/projects/{projectId}")
    public ResponseEntity<?> legacyUpdate(
            @PathVariable Long projectId,
            @RequestBody AdminAppAssignmentRequest req,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            if (req.getSlug() == null || req.getSlug().isBlank()) {
                return err(HttpStatus.BAD_REQUEST, "slug is required");
            }

            service.updateLicense(adminId, projectId, req.getSlug(), req);
            return ResponseEntity.noContent().build();

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update app license");
        }
    }

    @Deprecated
    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<?> legacyRemove(
            @PathVariable Long projectId,
            @RequestParam(name = "slug", required = false) String slug,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            Long adminId = adminIdFromTokenOrThrow(authHeader);

            if (slug == null || slug.isBlank()) {
                return err(HttpStatus.BAD_REQUEST, "slug is required");
            }

            service.remove(adminId, projectId, slug);
            return ResponseEntity.noContent().build();

        } catch (SecurityException se) {
            return forbidden("Forbidden");
        } catch (IllegalArgumentException iae) {
            return unauthorized(iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to remove app");
        }
    }
}