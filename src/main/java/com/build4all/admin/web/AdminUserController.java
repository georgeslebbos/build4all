package com.build4all.admin.web;

import com.build4all.user.dto.UserSummaryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.build4all.security.JwtUtil;
import com.build4all.admin.dto.AdminUserProfileDTO;
import com.build4all.admin.dto.AdminUserUpdateProfileRequest;
import com.build4all.admin.service.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired private AdminUserService adminUserService;
    @Autowired private JwtUtil jwtUtil;

    /* ===================== Response helpers ===================== */

    private ResponseEntity<Map<String, Object>> ok(String message) {
        return ResponseEntity.ok(new LinkedHashMap<>(Map.of("message", message)));
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> err(HttpStatus status, String error) {
        return ResponseEntity.status(status).body(new LinkedHashMap<>(Map.of("error", error)));
    }

    /* ===================== Auth helpers ===================== */

    private String requireBearer(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Unauthorized - missing or invalid token");
        }
        return authHeader.substring(7).trim();
    }

    /**
     * ✅ Validate token (shared)
     */
    private String requireValidToken(String authHeader) {
        String token = requireBearer(authHeader);

        try {
            if (!jwtUtil.validateToken(token)) {
                throw new IllegalArgumentException("Unauthorized - invalid or expired token");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unauthorized - invalid or expired token");
        }
        return token;
    }

    /**
     * ✅ Admin-only endpoints (list/by-role/others)
     */
    private String requireAdmin(String authHeader) {
        String token = requireValidToken(authHeader);

        // isAdminToken should include ADMIN/SUPER_ADMIN/MANAGER/OWNER if they are back-office
        // If your isAdminToken excludes OWNER, keep requireAdminOrOwner for /me only.
        if (!jwtUtil.isAdminToken(token)) {
            throw new SecurityException("Forbidden");
        }
        return token;
    }

    /**
     * ✅ /me endpoints: allow ADMIN or OWNER (your JwtUtil already has isAdminOrOwner)
     */
    private String requireAdminOrOwner(String authHeader) {
        String token = requireValidToken(authHeader);

        if (!jwtUtil.isAdminOrOwner(token)) {
            throw new SecurityException("Forbidden");
        }
        return token;
    }

    /**
     * ✅ IMPORTANT FIX:
     * Extract "backoffice id" from token:
     * - try adminId claim
     * - fallback to id claim (in case some tokens use id)
     */
    private Long requireBackofficeId(String token) {
        Long id = null;

        try { id = jwtUtil.extractAdminId(token); } catch (Exception ignored) {}
        if (id == null || id <= 0) {
            try { id = jwtUtil.extractId(token); } catch (Exception ignored) {}
        }

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Token missing id claim");
        }
        return id;
    }

    /* ===================== 1) GET all users+admins (admin only) ===================== */

    @Operation(summary = "Get all users and admins", description = "Get all users and admins")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllUsersAndAdmins(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            requireAdmin(authHeader);

            List<UserSummaryDTO> users = adminUserService.getAllUserSummaries();
            return ResponseEntity.ok(users);

        } catch (SecurityException se) {
            return err(HttpStatus.FORBIDDEN, "Forbidden: Admin access required");
        } catch (IllegalArgumentException iae) {
            return err(HttpStatus.UNAUTHORIZED, iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /* ===================== 2) GET by role (admin only) ===================== */

    @GetMapping("/by-role")
    public ResponseEntity<?> getUsersByRole(
            @RequestParam String role,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            requireAdmin(authHeader);

            List<UserSummaryDTO> users = adminUserService.getUsersByRole(role);
            return ResponseEntity.ok(users);

        } catch (SecurityException se) {
            return err(HttpStatus.FORBIDDEN, "Forbidden: Admin access required");
        } catch (IllegalArgumentException iae) {
            return err(HttpStatus.UNAUTHORIZED, iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /* ===================== 3) Public: check super admin exists ===================== */

    @GetMapping("/check-super-admin")
    public ResponseEntity<?> checkIfSuperAdminExists() {
        try {
            boolean exists = adminUserService.hasSuperAdmin();
            return ResponseEntity.ok(Map.of("hasSuperAdmin", exists));
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    /* ===================== 4) GET admin by id (self-only unless SUPER_ADMIN) ===================== */

    @GetMapping("/{adminId:\\d+}")
    public ResponseEntity<?> getAdminById(
            @PathVariable Long adminId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            String token = requireAdmin(authHeader);

            boolean superAdmin = jwtUtil.isSuperAdmin(token);
            Long tokenId = requireBackofficeId(token);

            if (!superAdmin && !tokenId.equals(adminId)) {
                return err(HttpStatus.FORBIDDEN, "Forbidden: not allowed to view this profile");
            }

            var admin = adminUserService.requireById(adminId);
            var dto = adminUserService.toProfileDTO(admin);
            return ResponseEntity.ok(dto);

        } catch (NoSuchElementException nf) {
            return err(HttpStatus.NOT_FOUND, nf.getMessage() == null ? "Admin not found" : nf.getMessage());
        } catch (SecurityException se) {
            return err(HttpStatus.FORBIDDEN, "Forbidden");
        } catch (IllegalArgumentException iae) {
            return err(HttpStatus.UNAUTHORIZED, iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /* ===================== 5) GET /me (ADMIN or OWNER) ✅ FIXED ===================== */

    @GetMapping("/me")
    public ResponseEntity<?> getMyAdminProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        try {
            String token = requireAdminOrOwner(authHeader);

            Long myId = requireBackofficeId(token);

            var admin = adminUserService.requireById(myId);
            var dto = adminUserService.toProfileDTO(admin);

            return ResponseEntity.ok(dto);

        } catch (NoSuchElementException nf) {
            return err(HttpStatus.NOT_FOUND, nf.getMessage() == null ? "Admin not found" : nf.getMessage());
        } catch (SecurityException se) {
            return err(HttpStatus.FORBIDDEN, "Forbidden");
        } catch (IllegalArgumentException iae) {
            return err(HttpStatus.UNAUTHORIZED, iae.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /* ===================== 6) PATCH /me (ADMIN or OWNER) ✅ FIXED ===================== */

    @PatchMapping("/me")
    public ResponseEntity<?> updateMyAdminProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody AdminUserUpdateProfileRequest req
    ) {
        try {
            String token = requireAdminOrOwner(authHeader);

            Long myId = requireBackofficeId(token);

            AdminUserProfileDTO dto = adminUserService.updateAdminProfile(myId, req);
            return ok("Profile updated", dto);

        } catch (RuntimeException re) {
            return err(HttpStatus.BAD_REQUEST, re.getMessage() == null ? "Bad request" : re.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    /* ===================== 7) PATCH /{adminId} (self-only unless SUPER_ADMIN) ===================== */

    @PatchMapping("/{adminId:\\d+}")
    public ResponseEntity<?> updateAdminProfileById(
            @PathVariable Long adminId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody AdminUserUpdateProfileRequest req
    ) {
        try {
            String token = requireAdmin(authHeader);

            boolean superAdmin = jwtUtil.isSuperAdmin(token);
            Long tokenId = requireBackofficeId(token);

            if (!superAdmin && !tokenId.equals(adminId)) {
                return err(HttpStatus.FORBIDDEN, "Forbidden: not allowed to edit this profile");
            }

            AdminUserProfileDTO dto = adminUserService.updateAdminProfile(adminId, req);
            return ok("Profile updated", dto);

        } catch (RuntimeException re) {
            return err(HttpStatus.BAD_REQUEST, re.getMessage() == null ? "Bad request" : re.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }
}