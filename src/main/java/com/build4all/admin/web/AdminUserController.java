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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin/users")
/**
 * Controller for admin-side user management endpoints.
 *
 * Main purpose:
 * - Provide lists of users/admins for dashboard screens.
 * - Provide profile reading endpoints (get admin by id, get "me").
 *
 * Authorization model used here:
 * - Uses JwtUtil helper methods (isAdminToken / isSuperAdmin / isAdminOrOwner)
 * - Reads the JWT from the Authorization header: "Bearer <token>"
 */
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private JwtUtil jwtUtil;

    @Operation(summary = "Get all users and admins", description = "Get all users and admins")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
            @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
            @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
            @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
            @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @GetMapping
    public ResponseEntity<?> getAllUsersAndAdmins(@RequestHeader("Authorization") String authHeader) {
        try {
            // Strip the "Bearer " prefix to get the raw token.
            String token = authHeader.replace("Bearer ", "").trim();

            // Only allow admin tokens (any admin role) to access this list.
            if (!jwtUtil.isAdminToken(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden: Admin access required"));
            }

            // Returns a merged list: regular users + admins, as UserSummaryDTO items.
            List<UserSummaryDTO> users = adminUserService.getAllUserSummaries();
            return ResponseEntity.ok(users);

        } catch (Exception e) {
            // Avoid leaking internal exceptions to clients; here you return the message for debugging.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error", "error", e.getMessage()));
        }
    }

    @Operation(summary = "Get users by role", description = "Fetch all users or admins by specified role")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
            @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
            @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
            @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
            @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    })
    @GetMapping("/by-role")
    public ResponseEntity<?> getUsersByRole(
            @RequestParam String role,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "").trim();

            // Must be an admin token (any admin role).
            if (!jwtUtil.isAdminToken(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden: Admin access required"));
            }

            // Delegates filtering logic to service:
            // - role=USER => list from Users table
            // - else => list from AdminUser table filtered by role name
            List<UserSummaryDTO> users = adminUserService.getUsersByRole(role);
            return ResponseEntity.ok(users);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error", "error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Check if a Super Admin exists",
            description = "Returns true if at least one SUPER_ADMIN exists in the database"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Check completed successfully"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/check-super-admin")
    public ResponseEntity<?> checkIfSuperAdminExists() {
        try {
            // Uses countByRole_NameIgnoreCase("SUPER_ADMIN") in repository via service.
            boolean exists = adminUserService.hasSuperAdmin();
            return ResponseEntity.ok(Map.of("hasSuperAdmin", exists));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Server error", "error", e.getMessage()));
        }
    }

    // --- keep imports & class header as-is ---
    // ................ existing endpoints ................

    /**
     * Get an admin profile by id.
     *
     * Access rules:
     * - SUPER_ADMIN can fetch any adminId
     * - Non-super-admin can only fetch their own profile (adminId must match token id)
     *
     * This protects admins from reading other admins' profiles unless they are SUPER_ADMIN.
     */
    @GetMapping("/{adminId}")
    public ResponseEntity<?> getAdminById(
            @PathVariable Long adminId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            final String token = authHeader.replace("Bearer ", "").trim();

            // allow SUPER_ADMIN to read anyone, others can only read their own profile
            final boolean superAdmin = jwtUtil.isSuperAdmin(token);

            // Extracts admin id from token (your JwtUtil must put this claim in the admin token).
            final Long tokenId = jwtUtil.extractAdminId(token); // claim "id" in your admin token

            // If not super admin, enforce self-only access.
            if (!superAdmin && (tokenId == null || !tokenId.equals(adminId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden: not allowed to view this profile"));
            }

            // requireById throws NoSuchElementException if not found.
            final var admin = adminUserService.requireById(adminId);

            // Convert entity -> DTO (no password, no relations).
            final var dto = adminUserService.toProfileDTO(admin);

            return ResponseEntity.ok(dto);

        } catch (NoSuchElementException nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", nf.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error", "error", e.getMessage()));
        }
    }

    /**
     * Get current admin profile based on the token ("me" endpoint).
     *
     * Access rules:
     * - Allows OWNER and other admin roles (not only SUPER_ADMIN)
     * - Uses token to extract the current adminId then returns the profile DTO
     *
     * Useful for: profile screen, showing current logged-in admin info.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyAdminProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            final String token = authHeader.replace("Bearer ", "").trim();

            // ✅ allow OWNER *and* admin roles to hit /me
            if (!jwtUtil.isAdminOrOwner(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden: login required"));
            }

            // Extract admin id from token. If missing => token format is not what backend expects.
            final Long adminId = jwtUtil.extractAdminId(token); // "id" in token
            if (adminId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Token missing id claim"));
            }

            final var admin = adminUserService.requireById(adminId);
            final var dto = adminUserService.toProfileDTO(admin);

            return ResponseEntity.ok(dto);

        } catch (NoSuchElementException nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", nf.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error", "error", e.getMessage()));
        }
    }
    
    @PatchMapping("/me")
    public ResponseEntity<?> updateMyAdminProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AdminUserUpdateProfileRequest req
    ) {
        try {
            final String token = authHeader.replace("Bearer ", "").trim();

            if (!jwtUtil.isAdminOrOwner(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden: login required"));
            }

            final Long adminId = jwtUtil.extractAdminId(token);
            if (adminId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Token missing id claim"));
            }

            AdminUserProfileDTO dto = adminUserService.updateAdminProfile(adminId, req);
            return ResponseEntity.ok(Map.of("message", "Profile updated", "data", dto));

        } catch (RuntimeException re) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", re.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error", "error", e.getMessage()));
        }
    }

    @PatchMapping("/{adminId}")
    public ResponseEntity<?> updateAdminProfileById(
            @PathVariable Long adminId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AdminUserUpdateProfileRequest req
    ) {
        try {
            final String token = authHeader.replace("Bearer ", "").trim();

            if (!jwtUtil.isAdminToken(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden: Admin access required"));
            }

            final boolean superAdmin = jwtUtil.isSuperAdmin(token);
            final Long tokenId = jwtUtil.extractAdminId(token);

            // Only SUPER_ADMIN can edit other admins. Everyone else: self only.
            if (!superAdmin && (tokenId == null || !tokenId.equals(adminId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden: not allowed to edit this profile"));
            }

            AdminUserProfileDTO dto = adminUserService.updateAdminProfile(adminId, req);
            return ResponseEntity.ok(Map.of("message", "Profile updated", "data", dto));

        } catch (RuntimeException re) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", re.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error", "error", e.getMessage()));
        }
    }

}
