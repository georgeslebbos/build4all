package com.build4all.user.web;

import com.build4all.security.JwtUtil;
import com.build4all.user.domain.UserStatus;
import com.build4all.user.domain.Users;
import com.build4all.user.dto.UserDto;
import com.build4all.user.repository.UserStatusRepository;
import com.build4all.user.repository.UsersRepository;
import com.build4all.user.service.UserService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * New tenant-aware UsersController.
 * Key points:
 * - Endpoints that fetch/modify data by user id require ownerProjectLinkId (tenant).
 * - Always return DTOs or simple maps (avoid lazy proxies).
 * - /profile-visibility is a PUT that updates the caller's visibility using JWT.
 */
@RestController
@RequestMapping("/api/users")
public class UsersController {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsersRepository usersRepository;
    @Autowired private UserStatusRepository userStatusRepository;

    private final UserService userService;
    public UsersController(UserService userService) { this.userService = userService; }

    /* ------------------------ LIST (tenant-scoped) ------------------------ */
    @ApiResponses({
        @ApiResponse(responseCode="200"),
        @ApiResponse(responseCode="401"),
        @ApiResponse(responseCode="403")
    })
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
                                         @RequestParam Long ownerProjectLinkId) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("message","Missing or invalid token"));
            }
            String token = authHeader.substring(7).trim();

            String role = jwtUtil.extractRole(token);
            if (jwtUtil.isBusinessToken(token)
                    || "SUPER_ADMIN".equalsIgnoreCase(role)
                    || role == null
                    || "USER".equalsIgnoreCase(role)) {
                return ResponseEntity.ok(userService.getAllUserDtos(ownerProjectLinkId));
            }
            return ResponseEntity.status(403).body(Map.of("message","Access denied"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message","Invalid or expired token"));
        }
    }

    /* ------------------------ GET ONE (tenant-scoped) ------------------------ */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id,
                                         @RequestParam Long ownerProjectLinkId) {
        try {
            Users user = userService.getUserById(id, ownerProjectLinkId);
            return ResponseEntity.ok(new UserDto(user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /* ------------------------ DELETE (self or SUPER_ADMIN) ------------------------ */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id,
                                             @RequestBody Map<String, String> body,
                                             @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }
            String token = authHeader.substring(7).trim();

            String role = jwtUtil.extractRole(token);
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
                boolean deleted = userService.deleteUserById(id);
                return deleted ? ResponseEntity.ok("User deleted by SUPER_ADMIN successfully")
                               : ResponseEntity.status(404).body("User not found");
            }

            String contact = jwtUtil.extractUsername(token);
            Users acting = usersRepository.findByEmail(contact);
            if (acting == null) acting = usersRepository.findByPhoneNumber(contact);
            if (acting == null || !Objects.equals(acting.getId(), id)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            String password = body.get("password");
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Password is required");
            }

            boolean deleted = userService.deleteUserByIdWithPassword(id, password);
            return deleted ? ResponseEntity.ok("User deleted successfully")
                           : ResponseEntity.status(403).body("Invalid password or user not found");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    /* ------------------------ PASSWORD RESET (tenant-scoped) ------------------------ */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> sendResetCode(@RequestBody Map<String, String> body,
                                                             @RequestParam Long ownerProjectLinkId) {
        try {
            String email = body.get("email");
            boolean ok = userService.resetPassword(email, ownerProjectLinkId);
            return ok ? ResponseEntity.ok(Map.of("message", "Reset code sent"))
                      : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyCode(@RequestBody Map<String, String> body,
                                                          @RequestParam Long ownerProjectLinkId) {
        try {
            String email = body.get("email");
            String code  = body.get("code");
            return userService.verifyResetCode(email, code, ownerProjectLinkId)
                    ? ResponseEntity.ok(Map.of("message", "Code verified successfully"))
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid code"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    @PostMapping("/update-password")
    public ResponseEntity<Map<String, String>> updatePassword(@RequestBody Map<String, String> body,
                                                              @RequestParam Long ownerProjectLinkId) {
        try {
            String email = body.get("email");
            String code  = body.get("code");
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "New password is required"));
            }
            boolean ok = userService.updatePassword(email, code, newPassword, ownerProjectLinkId);
            return ok ? ResponseEntity.ok(Map.of("message", "Password updated successfully"))
                      : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid code or user"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    /* ------------------------ SELF: PROFILE VISIBILITY (no tenant param; from JWT) ------------------------ */
    @PutMapping("/profile-visibility")
    public ResponseEntity<?> updateProfileVisibility(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
                                                     @RequestParam("isPublic") boolean isPublic) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Missing or invalid token"));
        }
        String token = authHeader.substring(7).trim();

        String contact = jwtUtil.extractUsername(token);
        Users user = usersRepository.findByEmail(contact);
        if (user == null) user = usersRepository.findByPhoneNumber(contact);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found from token"));
        }

        user.setIsPublicProfile(isPublic);
        user.setUpdatedAt(LocalDateTime.now());
        usersRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Profile visibility updated", "isPublic", isPublic));
    }

    /* ------------------------ SELF: STATUS ------------------------ */
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid token");
        }
        String contact = jwtUtil.extractUsername(authHeader.substring(7).trim());
        Users user = usersRepository.findByEmail(contact);
        if (user == null) user = usersRepository.findByPhoneNumber(contact);

        if (user == null || !Objects.equals(user.getId(), id)) return ResponseEntity.status(403).body("Access denied");

        String statusStr = body.getOrDefault("status", "");
        String password  = body.get("password");
        if (statusStr.isBlank()) return ResponseEntity.badRequest().body("Status is required");

        if ("INACTIVE".equalsIgnoreCase(statusStr)) {
            if (password == null || password.isBlank()) return ResponseEntity.badRequest().body("Password is required to deactivate account.");
            if (!userService.checkPassword(user, password)) return ResponseEntity.status(401).body("Incorrect password. Status not changed.");
        }

        Optional<UserStatus> newStatusOpt = userStatusRepository.findByNameIgnoreCase(statusStr);
        if (newStatusOpt.isEmpty()) return ResponseEntity.badRequest().body("Invalid status value");

        user.setStatus(newStatusOpt.get());
        user.setUpdatedAt(LocalDateTime.now());
        usersRepository.save(user);
        return ResponseEntity.ok("User status updated to " + newStatusOpt.get().getName());
    }

    /* ------------------------ FRIEND SUGGESTIONS (optional) ------------------------ */
    @GetMapping("/{userId}/suggestions")
    public ResponseEntity<?> getFriendSuggestions(@PathVariable Long userId) {
        try {
            List<Users> suggestions = userService.getAllUsers(/* any tenant? handled internally if needed */ null);
            // If you have a tenant param for suggestions, add @RequestParam Long ownerProjectLinkId and use it.
            List<UserDto> result = suggestions.stream().map(UserDto::new).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error fetching suggestions", "details", e.getMessage()));
        }
    }

    /* ------------------------ CATEGORIES (by user id – NOT tenanted here; add if needed) ------------------------ */
    @GetMapping("/{userId}/categories")
    public ResponseEntity<?> getUserCategories(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(userService.getUserCategories(userId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to fetch categories");
        }
    }

    @PostMapping("/{userId}/categories")
    public ResponseEntity<?> addUserCategories(@PathVariable Long userId,
                                               @RequestBody List<Long> categoryIds) {
        userService.replaceUserCategories(userId, categoryIds); // replace for idempotency; or call addUserCategories if you have it
        return ResponseEntity.ok(Map.of("message", "User categories updated successfully"));
    }

    @PutMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> updateUserCategory(@PathVariable Long userId,
                                                @PathVariable Long categoryId,
                                                @RequestBody Map<String, String> body) {
        try {
            String newCategoryName = body.get("name");
            if (newCategoryName == null || newCategoryName.isBlank()) {
                return ResponseEntity.badRequest().body("Category name is required");
            }
            boolean updated = userService.updateUserCategory(userId, categoryId, newCategoryName);
            return updated ? ResponseEntity.ok("Category updated successfully")
                           : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Category not found for this user");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating category");
        }
    }

    @DeleteMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> deleteUserCategory(@PathVariable Long userId,
                                                @PathVariable Long categoryId) {
        try {
            boolean deleted = userService.deleteUserCategory(userId, categoryId);
            return deleted ? ResponseEntity.ok("Category removed successfully")
                           : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Category not found for this user");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete category");
        }
    }

    /* ------------------------ OPTIONAL: VISIBILITY + STATUS IN ONE CALL ------------------------ */
    @PutMapping("/{id}/visibility-status")
    public ResponseEntity<String> updateVisibilityAndStatus(@PathVariable Long id,
                                                            @RequestParam boolean isPublicProfile,
                                                            @RequestParam String status) {
        Optional<UserStatus> newStatusOpt = userStatusRepository.findByNameIgnoreCase(status);
        if (newStatusOpt.isEmpty()) return ResponseEntity.badRequest().body("Invalid status value");
        boolean updated = userService.updateVisibilityAndStatus(id, isPublicProfile, newStatusOpt.get());
        return updated ? ResponseEntity.ok("Visibility and status updated successfully.")
                       : ResponseEntity.status(404).body("User not found.");
    }
}
