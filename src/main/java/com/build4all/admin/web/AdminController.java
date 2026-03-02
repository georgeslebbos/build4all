package com.build4all.admin.web;

import com.build4all.authentication.dto.AdminPasswordUpdateDTO;
import com.build4all.admin.dto.AdminProfileUpdateDTO;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.business.repository.BusinessStatusRepository;
import com.build4all.catalog.dto.AdminItemDTO;
import com.build4all.review.repository.ReviewRepository;
import com.build4all.user.repository.UserStatusRepository;
import com.build4all.user.repository.UsersRepository;
import com.build4all.business.service.BusinessService;
import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import com.build4all.user.domain.Users;
import com.build4all.notifications.dto.AdminNotificationPreferencesDTO;
import com.build4all.catalog.service.AdminItemService;
import com.build4all.admin.service.AdminStatsService;
import com.build4all.admin.service.AdminUserService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/superadmin")
@Tag(name = "Admin Dashboard", description = "SUPER_ADMIN-only statistics and system management")
@PreAuthorize("hasRole('SUPER_ADMIN')") // ✅ blocks non-superadmin BEFORE entering methods
public class AdminController {

    @Autowired private AdminStatsService statsService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private AdminItemService adminItemService;
    @Autowired private UsersRepository usersRepository;
    @Autowired private AdminUsersRepository adminUsersRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private BusinessStatusRepository businessStatusRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private BusinessService businessService;
    @Autowired private UserStatusRepository userStatusRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /* =====================================================
       Helpers: consistent auth + responses
       ===================================================== */

    private ResponseEntity<Map<String, Object>> ok(String msg) {
        return ResponseEntity.ok(new LinkedHashMap<>(Map.of("message", msg)));
    }

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
        return ResponseEntity.ok(new LinkedHashMap<>(body));
    }

    private ResponseEntity<Map<String, Object>> err(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(new LinkedHashMap<>(Map.of("error", msg)));
    }

    /**
     * Extract raw JWT from Authorization header.
     * Expected: "Authorization: Bearer <jwt>"
     */
    private String requireBearer(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7).trim();
    }

    /**
     * SUPER_ADMIN guard:
     * - validates token (optional, depends on your JwtUtil implementation)
     * - verifies role == SUPER_ADMIN
     * Returns raw jwt.
     */
    private String requireSuperAdminJwt(String authHeader) {
        String jwt = requireBearer(authHeader);

        // If your JwtUtil.validateToken throws, wrap it.
        try {
            if (!jwtUtil.validateToken(jwt)) {
                throw new IllegalArgumentException("Invalid or expired token");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        String role = jwtUtil.extractRole(jwt);
        if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
            // With @PreAuthorize this is mostly redundant, but keep it for safety
            throw new IllegalArgumentException("Forbidden");
        }
        return jwt;
    }

    /* =====================================================
       1) STATS
       ===================================================== */

    @GetMapping("/stats")
    @Operation(summary = "Get system stats", description = "Returns totals based on selected period")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<?> getStats(
            @RequestParam(defaultValue = "today") String period,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth); // ✅ just to validate token + role
            return ResponseEntity.ok(statsService.getStats(period));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load stats");
        }
    }

    @GetMapping("/registrations/monthly")
    @Operation(summary = "Get monthly user registration counts", description = "Returns registration counts per month (last 6 months)")
    public ResponseEntity<?> getMonthlyUserRegistrations(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);
            Map<String, Long> registrations = statsService.getMonthlyRegistrations();
            return ResponseEntity.ok(registrations);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load registrations");
        }
    }

    @GetMapping("/activities/popular")
    @Operation(summary = "Get popular activities", description = "Returns most booked or viewed items and metrics")
    public ResponseEntity<?> getPopularActivities(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);
            List<Map<String, Object>> activities = statsService.getPopularItems();
            return ResponseEntity.ok(activities);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load popular items");
        }
    }

    @GetMapping("/activities")
    @Operation(summary = "Get all activities posted by businesses", description = "Returns admin view of all items")
    public ResponseEntity<?> getAllActivities(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);
            List<AdminItemDTO> activities = adminItemService.getAllItems();
            return ResponseEntity.ok(activities);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load activities");
        }
    }

    /* =====================================================
       2) USERS MANAGEMENT
       ===================================================== */

    @PutMapping("/{userId}/toggle-status")
    @Operation(summary = "Toggle user status", description = "Toggle user between ACTIVE and INACTIVE")
    public ResponseEntity<?> toggleUserStatus(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);

            Optional<Users> optionalUser = usersRepository.findById(userId);
            if (optionalUser.isEmpty()) return err(HttpStatus.NOT_FOUND, "User not found");

            Users user = optionalUser.get();
            String currentStatus = user.getStatus() == null ? "" : user.getStatus().getName();

            if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
                user.setStatus(userStatusRepository.findByName("INACTIVE")
                        .orElseThrow(() -> new RuntimeException("INACTIVE status not found")));
            } else {
                user.setStatus(userStatusRepository.findByName("ACTIVE")
                        .orElseThrow(() -> new RuntimeException("ACTIVE status not found")));
            }

            usersRepository.save(user);
            return ok("User status updated to: " + user.getStatus().getName());

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to toggle user status");
        }
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Delete user", description = "Permanently delete a user account by ID")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);

            Optional<Users> optionalUser = usersRepository.findById(userId);
            if (optionalUser.isEmpty()) return err(HttpStatus.NOT_FOUND, "User not found");

            adminUserService.deleteUserAndDependencies(userId);
            return ok("User and all related data deleted successfully.");

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete user");
        }
    }

    /* =====================================================
       3) SUPER_ADMIN PROFILE
       ===================================================== */

    @PutMapping("/profile")
    @Operation(summary = "Update admin profile", description = "Update SUPER_ADMIN profile information")
    public ResponseEntity<?> updateAdminProfile(
            @RequestBody AdminProfileUpdateDTO dto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            String jwt = requireSuperAdminJwt(auth);
            Long currentAdminId = jwtUtil.extractId(jwt);

            AdminUser admin = adminUsersRepository.findById(currentAdminId).orElse(null);
            if (admin == null) return err(HttpStatus.NOT_FOUND, "Admin user not found.");

            admin.setFirstName(dto.getFirstName());
            admin.setLastName(dto.getLastName());
            admin.setUsername(dto.getUsername());
            admin.setEmail(dto.getEmail());

            adminUsersRepository.save(admin);
            return ok("Admin profile updated successfully.");

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update admin profile");
        }
    }

    @PutMapping("/password")
    @Operation(summary = "Update admin password", description = "Change SUPER_ADMIN password after verifying current password")
    public ResponseEntity<?> updateAdminPassword(
            @RequestBody AdminPasswordUpdateDTO dto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            String jwt = requireSuperAdminJwt(auth);
            Long currentAdminId = jwtUtil.extractId(jwt);

            AdminUser admin = adminUsersRepository.findById(currentAdminId).orElse(null);
            if (admin == null) return err(HttpStatus.NOT_FOUND, "Admin user not found.");

            if (!passwordEncoder.matches(dto.getCurrentPassword(), admin.getPasswordHash())) {
                return err(HttpStatus.FORBIDDEN, "Current password is incorrect.");
            }

            admin.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
            adminUsersRepository.save(admin);

            return ok("Password updated successfully.");

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update password");
        }
    }

    @PutMapping("/notifications")
    @Operation(summary = "Update notification preferences", description = "Update admin notification settings")
    public ResponseEntity<?> updateNotificationPreferences(
            @RequestBody AdminNotificationPreferencesDTO dto,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            String jwt = requireSuperAdminJwt(auth);
            Long currentAdminId = jwtUtil.extractId(jwt);

            AdminUser admin = adminUsersRepository.findById(currentAdminId).orElse(null);
            if (admin == null) return err(HttpStatus.NOT_FOUND, "Admin user not found.");

            admin.setNotifyItemUpdates(dto.isNotifyItemUpdates());
            admin.setNotifyUserFeedback(dto.isNotifyUserFeedback());
            adminUsersRepository.save(admin);

            return ok("Notification preferences updated successfully.");

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update notification preferences");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current admin profile", description = "Returns profile details of the logged-in SUPER_ADMIN")
    public ResponseEntity<?> getCurrentAdminProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            String jwt = requireSuperAdminJwt(auth);
            Long currentAdminId = jwtUtil.extractId(jwt);

            AdminUser admin = adminUsersRepository.findById(currentAdminId).orElse(null);
            if (admin == null) return err(HttpStatus.NOT_FOUND, "Admin not found.");

            return ok(Map.of(
                    "id", admin.getAdminId(),
                    "firstName", admin.getFirstName(),
                    "lastName", admin.getLastName(),
                    "username", admin.getUsername(),
                    "email", admin.getEmail(),
                    "notifyItemUpdates", admin.getNotifyItemUpdates(),
                    "notifyUserFeedback", admin.getNotifyUserFeedback()
            ));

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load profile");
        }
    }

    /* =====================================================
       4) FEEDBACK + BUSINESSES MANAGEMENT
       ===================================================== */

    @GetMapping("/feedback")
    @Operation(summary = "Get all feedback", description = "Returns all reviews/feedback in the system")
    public ResponseEntity<?> getAllFeedback(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);
            return ResponseEntity.ok(reviewRepository.findAll());
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load feedback");
        }
    }

    @DeleteMapping("/businesses/{businessId}")
    @Operation(summary = "Delete a business and all related data", description = "Only SUPER_ADMIN can delete a business")
    public ResponseEntity<?> deleteBusinessBySuperAdmin(
            @PathVariable Long businessId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) throws IllegalArgumentException {
        try {
            requireSuperAdminJwt(auth);
            businessService.delete(businessId);
            return ok("Business and all related data deleted successfully.");
        } catch (RuntimeException e) {
            return err(HttpStatus.NOT_FOUND, "Business not found.");
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete business");
        }
    }

    @PutMapping("/businesses/{businessId}/disable")
    @Operation(summary = "Disable a Business", description = "Only SUPER_ADMIN can disable a business")
    public ResponseEntity<?> disableBusiness(
            @PathVariable Long businessId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);

            Businesses business = businessService.findById(businessId);
            if (business == null) return err(HttpStatus.NOT_FOUND, "Business not found");

            BusinessStatus inactiveStatus = businessStatusRepository.findByNameIgnoreCase("INACTIVEBYADMIN")
                    .orElseThrow(() -> new RuntimeException("INACTIVEBYADMIN status not found in DB"));

            business.setStatus(inactiveStatus);
            businessService.save(business);

            return ok("Business marked as INACTIVEBYADMIN.");

        } catch (RuntimeException e) {
            return err(HttpStatus.NOT_FOUND, "Business not found or status issue.");
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to disable business");
        }
    }

    @PutMapping("/businesses/{businessId}/activate")
    @Operation(summary = "Reactivate a Business", description = "Only SUPER_ADMIN can reactivate a disabled business")
    public ResponseEntity<?> activateBusiness(
            @PathVariable Long businessId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            requireSuperAdminJwt(auth);

            Businesses business = businessService.findById(businessId);
            if (business == null) return err(HttpStatus.NOT_FOUND, "Business not found");

            BusinessStatus activeStatus = businessStatusRepository.findByNameIgnoreCase("ACTIVE")
                    .orElseThrow(() -> new RuntimeException("ACTIVE status not found"));

            business.setStatus(activeStatus);
            businessService.save(business);

            return ok(Map.of(
                    "message", "Business reactivated successfully",
                    "businessId", business.getId()
            ));

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if ("Forbidden".equalsIgnoreCase(msg)) return err(HttpStatus.FORBIDDEN, "Access denied");
            return err(HttpStatus.UNAUTHORIZED, msg.isBlank() ? "Unauthorized" : msg);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to reactivate business");
        }
    }
}