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
import java.util.stream.Collectors;

/**
 * UsersController
 * - Restores all endpoints from the legacy controller
 * - Updated to support tenant scoping via ownerProjectLinkId where needed
 * - Uses service methods you already updated (DTO fetch with fetch-join, tenant-aware lookups)
 */
@RestController
@RequestMapping("/api/users")
public class UsersController {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsersRepository usersRepository;
    @Autowired private UserStatusRepository userStatusRepository;

    private final UserService userService;
    public UsersController(UserService userService) { this.userService = userService; }

    /* =====================================================
       LIST ALL (tenant-scoped)
       ===================================================== */
    @ApiResponses({
        @ApiResponse(responseCode="200"),
        @ApiResponse(responseCode="401"),
        @ApiResponse(responseCode="403")
    })
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("message","Missing or invalid token"));
            }
            token = token.substring(7).trim();

            // Allow business tokens, SUPER_ADMIN, or regular user
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

    /* =====================================================
       GET ONE (tenant-scoped)
       ===================================================== */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(
            @PathVariable Long id,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            UserDto dto = userService.getUserDtoByIdAndOwnerProject(id, ownerProjectLinkId);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /* =====================================================
       DELETE USER (self or SUPER_ADMIN)
       ===================================================== */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String token
    ) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }
            token = token.substring(7).trim();

            String contact = jwtUtil.extractUsername(token);
            Users acting = usersRepository.findByEmail(contact);
            if (acting == null) acting = usersRepository.findByPhoneNumber(contact);

            String role = jwtUtil.extractRole(token);
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
                boolean deleted = userService.deleteUserById(id);
                return deleted
                        ? ResponseEntity.ok("User deleted by SUPER_ADMIN successfully")
                        : ResponseEntity.status(404).body("User not found");
            }

            if (acting == null || !Objects.equals(acting.getId(), id)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            String password = body.get("password");
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Password is required");
            }

            boolean deleted = userService.deleteUserByIdWithPassword(id, password);
            return deleted
                    ? ResponseEntity.ok("User deleted successfully")
                    : ResponseEntity.status(403).body("Invalid password or user not found");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    /* =====================================================
       PASSWORD RESET FLOW (tenant-scoped)
       ===================================================== */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> sendResetCode(
            @RequestBody Map<String, String> body,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            String email = body.get("email");
            boolean ok = userService.resetPassword(email, ownerProjectLinkId);
            return ok
                    ? ResponseEntity.ok(Map.of("message", "Reset code sent"))
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyCode(
            @RequestBody Map<String, String> body,
            @RequestParam Long ownerProjectLinkId
    ) {
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
    public ResponseEntity<Map<String, String>> updatePassword(
            @RequestBody Map<String, String> body,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            String email = body.get("email");
            String code  = body.get("code");
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "New password is required"));
            }
            boolean ok = userService.updatePassword(email, code, newPassword, ownerProjectLinkId);
            return ok
                    ? ResponseEntity.ok(Map.of("message", "Password updated successfully"))
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid code or user"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    /* =====================================================
       SELF STATUS UPDATE (requires password only for INACTIVE)
       ===================================================== */
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid token");
        }
        String contact = jwtUtil.extractUsername(token.substring(7).trim());
        Users user = usersRepository.findByEmail(contact);
        if (user == null) user = usersRepository.findByPhoneNumber(contact);

        if (user == null || !Objects.equals(user.getId(), id)) {
            return ResponseEntity.status(403).body("Access denied");
        }

        String statusStr = body.getOrDefault("status", "");
        String password  = body.get("password");
        if (statusStr.isBlank()) return ResponseEntity.badRequest().body("Status is required");

        if ("INACTIVE".equalsIgnoreCase(statusStr)) {
            if (password == null || password.isBlank())
                return ResponseEntity.badRequest().body("Password is required to deactivate account.");
            if (!userService.checkPassword(user, password))
                return ResponseEntity.status(401).body("Incorrect password. Status not changed.");
        }

        Optional<UserStatus> newStatusOpt = userStatusRepository.findByNameIgnoreCase(statusStr);
        if (newStatusOpt.isEmpty()) return ResponseEntity.badRequest().body("Invalid status value");

        user.setStatus(newStatusOpt.get());
        user.setUpdatedAt(LocalDateTime.now());
        usersRepository.save(user);
        return ResponseEntity.ok("User status updated to " + newStatusOpt.get().getName());
    }

    /* =====================================================
       ADMIN/GENERAL: UPDATE USER STATUS (duplicate legacy route)
       - Kept for backward compatibility with old clients
       ===================================================== */
    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> requestBody
    ) {
        String newStatus = requestBody.get("status");
        String password = requestBody.get("password");
        if (newStatus == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing status"));
        }

        try {
            Users user = userService.getUserById(id); // legacy/global lookup

            if ("INACTIVE".equalsIgnoreCase(newStatus)) {
                if (password == null || password.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Password is required to deactivate account."));
                }
                boolean isValid = userService.checkPassword(user, password);
                if (!isValid) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Incorrect password. Status not changed."));
                }
            }

            UserStatus statusEntity = userService.getStatus(newStatus);
            user.setStatus(statusEntity);
            user.setUpdatedAt(LocalDateTime.now());
            userService.save(user);

            return ResponseEntity.ok(Map.of("message", "User status updated successfully", "newStatus", newStatus));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /* =====================================================
       GOOGLE USERS: UPDATE STATUS (legacy)
       ===================================================== */
    @PutMapping("/auth/google/status")
    public ResponseEntity<?> updateGoogleUserStatus(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> requestBody
    ) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing or invalid token"));
            }

            String jwt = token.replace("Bearer ", "").trim();
            String email = jwtUtil.extractUsername(jwt);
            Users user = userService.getUserByEmaill(email);

            if (user == null || user.getGoogleId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Only Google users can use this endpoint"));
            }

            String newStatus = requestBody.get("status");
            if (newStatus == null || newStatus.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing status"));
            }

            UserStatus status = userService.getStatus(newStatus);
            user.setStatus(status);
            user.setUpdatedAt(LocalDateTime.now());
            userService.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Google account status updated",
                    "status", status.getName(),
                    "googleId", user.getGoogleId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update Google user status", "details", e.getMessage()));
        }
    }

    /* =====================================================
       PROFILE VISIBILITY (self) – accepts PUT and PATCH
       - Added ownerProjectLinkId to disambiguate tenant
       - Accepts ?isPublic=true|false
       ===================================================== */
    @PutMapping("/profile-visibility")
    @PatchMapping("/profile-visibility")
    public ResponseEntity<String> updateProfileVisibility(
            @RequestParam boolean isPublic,
            @RequestParam Long ownerProjectLinkId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }

            String contact = jwtUtil.extractUsername(token.substring(7).trim());
            Users user = usersRepository.findByEmailAndOwnerProject_Id(contact, ownerProjectLinkId);
            if (user == null) {
                user = usersRepository.findByPhoneNumberAndOwnerProject_Id(contact, ownerProjectLinkId);
            }

            if (user == null) {
                return ResponseEntity.status(404).body("User not found in this app");
            }

            user.setIsPublicProfile(isPublic);
            usersRepository.save(user);

            return ResponseEntity.ok("Profile visibility updated to " + (isPublic ? "PUBLIC" : "PRIVATE"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    /* =====================================================
       FRIEND SUGGESTIONS (legacy/global)
       ===================================================== */
    @GetMapping("/{userId}/suggestions")
    public ResponseEntity<?> getFriendSuggestions(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token
    ) {
        try {
            var suggestions = userService.suggestFriendsByCategory(userId);
            var result = suggestions.stream().map(UserDto::new).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching suggestions: " + e.getMessage());
        }
    }

    /* =====================================================
       USER CATEGORIES (legacy/global)
       ===================================================== */
    @PostMapping("/{userId}/categories")
    public ResponseEntity<?> addUserCategories(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds
    ) {
        userService.addUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories added successfully"));
    }

    @GetMapping("/{userId}/categories")
    public ResponseEntity<?> getUserCategories(@PathVariable Long userId) {
        try {
            List<String> categories = userService.getUserCategories(userId);
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to fetch categories");
        }
    }

    @PutMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> updateUserCategory(
            @PathVariable Long userId,
            @PathVariable Long categoryId,
            @RequestBody Map<String, String> body
    ) {
        try {
            String newCategoryName = body.get("name");
            if (newCategoryName == null || newCategoryName.isBlank()) {
                return ResponseEntity.badRequest().body("Category name is required");
            }

            boolean updated = userService.updateUserCategory(userId, categoryId, newCategoryName);
            return updated
                    ? ResponseEntity.ok("Category updated successfully")
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Category not found for this user");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating category");
        }
    }

    @DeleteMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> deleteUserCategory(
            @PathVariable Long userId,
            @PathVariable Long categoryId
    ) {
        try {
            boolean deleted = userService.deleteUserCategory(userId, categoryId);
            return deleted
                    ? ResponseEntity.ok("Category removed successfully")
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Category not found for this user");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete category");
        }
    }

    @PostMapping("/{userId}/update-category")
    public ResponseEntity<?> replaceUserCategories(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds
    ) {
        userService.replaceUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories replaced successfully"));
    }

    /* =====================================================
       DELETE PROFILE IMAGE (legacy/global)
       ===================================================== */
    @DeleteMapping("/delete-profile-image/{id}")
    public ResponseEntity<?> deleteProfileImage(@PathVariable Long id) {
        boolean success = userService.deleteUserProfileImage(id);
        return success
                ? ResponseEntity.ok("Profile image deleted successfully")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body("No profile image found or already deleted");
    }

    /* =====================================================
       VISIBILITY + STATUS IN ONE CALL (legacy/global)
       ===================================================== */
    @PutMapping("/{id}/visibility-status")
    public ResponseEntity<String> updateVisibilityAndStatus(
            @PathVariable Long id,
            @RequestParam boolean isPublicProfile,
            @RequestParam UserStatus status
    ) {
        boolean updated = userService.updateVisibilityAndStatus(id, isPublicProfile, status);
        return updated
                ? ResponseEntity.ok("Visibility and status updated successfully.")
                : ResponseEntity.status(404).body("User not found.");
    }
    
    
 // put this inside @RestController @RequestMapping("/api/users")
    @PostMapping({
        "/{userId}/update-category",   // new canonical (preferred)
        "/{userId}/categories",        // legacy
        "/{userId}/categoriess",       // legacy typo you had in apps
        "/{userId}/UpdateCategory",    // legacy PascalCase
        "/{userId}/UpdateInterest"     // legacy PascalCase 2
    })
    public ResponseEntity<?> replaceUserCategoriesCompat(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds
    ) {
        userService.replaceUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories replaced successfully"));
    }

}
