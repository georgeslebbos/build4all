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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UsersController
 *
 * Goal of comments:
 * ✅ Show which calls hit DB + the equivalent SQL idea.
 *
 * Notes:
 * - Controller itself doesn’t generate SQL; repositories do.
 * - When you call userService.*, the SQL happens inside service/repository.
 *   I still annotate the *effective SQL* of the called repository methods.
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

            // No SQL here: extracting claims from JWT is pure computation.
            String role = jwtUtil.extractRole(token);

            // No SQL here either: jwtUtil.isBusinessToken(token) depends on your implementation (usually claims check).
            if (jwtUtil.isBusinessToken(token)
                    || "SUPER_ADMIN".equalsIgnoreCase(role)
                    || role == null
                    || "USER".equalsIgnoreCase(role)) {

                /**
                 * SQL executed by userService.getAllUserDtos(ownerProjectLinkId)
                 *
                 * In your current UserService, getAllUserDtos() does:
                 *   1) linkById(ownerProjectLinkId) -> aupRepo.findById(...)
                 *      SQL: SELECT * FROM admin_user_project WHERE id = :ownerProjectLinkId LIMIT 1;
                 *
                 *   2) userRepository.findAll()
                 *      SQL: SELECT * FROM users;
                 *
                 *   3) Java filters for:
                 *      - tenant match (aup_id)
                 *      - status ACTIVE
                 *      - is_public_profile = true
                 *
                 * ⚠️ Optimization suggestion (not required): replace findAll()+filter with a repository method:
                 *    SELECT * FROM users
                 *    WHERE aup_id=:ownerProjectLinkId
                 *      AND status = (SELECT id FROM user_status WHERE name='ACTIVE')
                 *      AND is_public_profile = true;
                 */
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
            @RequestParam Long ownerProjectLinkId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            // 1) Require Bearer token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Missing or invalid token"));
            }

            String jwt = authHeader.substring(7).trim();

            // 2) Validate token
            if (!jwtUtil.validateToken(jwt)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired token"));
            }

            String role = Optional.ofNullable(jwtUtil.extractRole(jwt))
                    .orElse("")
                    .toUpperCase();

            // 3) Enforce tenant match for tenant-scoped roles
            // SUPER_ADMIN may be global and may not have ownerProjectId claim
            if (!"SUPER_ADMIN".equals(role)) {
                try {
                    jwtUtil.requireTenantMatch(jwt, ownerProjectLinkId);
                } catch (RuntimeException e) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Tenant mismatch"));
                }
            }

            // 4) Object-level authorization (IDOR/BOLA fix)
            switch (role) {
                case "SUPER_ADMIN":
                case "OWNER":
                case "MANAGER":
                    // allowed (already tenant-checked except SUPER_ADMIN)
                    break;

                case "USER":
                    Long tokenUserId = jwtUtil.extractId(jwt);
                    if (!Objects.equals(tokenUserId, id)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "Access denied"));
                    }
                    break;

                // If you do NOT want businesses to access user details, deny explicitly
                case "BUSINESS":
                default:
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Access denied"));
            }

            // 5) Fetch only after authz passes
            UserDto dto = userService.getUserDtoByIdAndOwnerProject(id, ownerProjectLinkId);
            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // Avoid leaking whether user exists in another tenant (optional hardening)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error"));
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

            String jwt = token.substring(7).trim();
            String role = jwtUtil.extractRole(jwt);

            // ✅ SUPER_ADMIN can delete any user
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
                boolean deleted = userService.deleteUserById(id);
                return deleted
                        ? ResponseEntity.ok("User deleted by SUPER_ADMIN successfully")
                        : ResponseEntity.status(404).body("User not found");
            }

            // ✅ SELF delete: use userId from token (NOT email/phone)
            Long tokenUserId = jwtUtil.extractId(jwt);
            if (tokenUserId == null || tokenUserId <= 0) {
                return ResponseEntity.status(401).body("Invalid token: missing user id");
            }

            if (!Objects.equals(tokenUserId, id)) {
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

            /**
             * SQL inside userService.resetPassword(email, ownerProjectLinkId):
             *   SELECT * FROM users
             *   WHERE email=:email AND aup_id=:ownerProjectLinkId
             *   LIMIT 1;
             *
             * Reset code is stored in-memory (Map) -> no SQL.
             * Sending email -> no SQL.
             */
            boolean ok = userService.resetPassword(email, ownerProjectLinkId);

            return ok
                    ? ResponseEntity.ok(Map.of("message", "Reset code sent"))
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }
    
    
 // src/main/java/com/build4all/user/web/UsersController.java

    @PutMapping(value = "/{id}/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateUserProfile(
            @PathVariable Long id,
            @RequestParam Long ownerProjectLinkId,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean isPublicProfile,
            @RequestParam(required = false) Boolean imageRemoved,
            @RequestPart(required = false) MultipartFile profileImage,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error","Missing or invalid token"));
            }

            String jwt = token.substring(7).trim();
            Long tokenUserId = jwtUtil.extractId(jwt); // ✅ userId from claim

            Users updated = userService.updateUserProfile(
                    id, ownerProjectLinkId, tokenUserId,
                    username, firstName, lastName,
                    isPublicProfile, profileImage,
                    imageRemoved
            );


            return ResponseEntity.ok(Map.of(
                    "message", "Profile updated successfully",
                    "user", new UserDto(updated)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "Unexpected error" : e.getMessage();

            if ("Access denied".equalsIgnoreCase(msg)) {
                return ResponseEntity.status(403).body(Map.of("error", msg));
            }

            // ✅ duplicate username should be 409, not 404
            if (msg.toLowerCase().contains("username already")) {
                return ResponseEntity.status(409).body(Map.of("error", msg));
            }

            return ResponseEntity.status(404).body(Map.of("error", msg));
        }catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Server error: " + e.getMessage()));
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

            /**
             * userService.verifyResetCode(...) uses in-memory Map only.
             * ✅ No SQL.
             */
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

            /**
             * SQL inside userService.updatePassword(...):
             * 1) verifyResetCode -> no SQL (in-memory)
             * 2) find user by tenant:
             *    SELECT * FROM users WHERE email=:email AND aup_id=:ownerProjectLinkId LIMIT 1;
             * 3) UPDATE password hash:
             *    UPDATE users SET password_hash=:hash WHERE user_id=:id;
             */
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
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }

            String jwt = token.substring(7).trim();

            // ✅ Use userId from token
            Long tokenUserId = jwtUtil.extractId(jwt);
            if (tokenUserId == null || tokenUserId <= 0) {
                return ResponseEntity.status(401).body("Invalid token: missing user id");
            }

            if (!Objects.equals(tokenUserId, id)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            // ✅ Fetch user by id (no email/phone guessing)
            Users user = usersRepository.findById(id).orElse(null);
            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            String statusStr = body.getOrDefault("status", "").trim();
            String password = body.get("password");

            if (statusStr.isBlank()) {
                return ResponseEntity.badRequest().body("Status is required");
            }

            if ("INACTIVE".equalsIgnoreCase(statusStr)) {
                if (password == null || password.isBlank()) {
                    return ResponseEntity.badRequest().body("Password is required to deactivate account.");
                }

                if (!userService.checkPassword(user, password)) {
                    return ResponseEntity.status(401).body("Incorrect password. Status not changed.");
                }
            }

            Optional<UserStatus> newStatusOpt = userStatusRepository.findByNameIgnoreCase(statusStr);
            if (newStatusOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid status value");
            }

            user.setStatus(newStatusOpt.get());
            user.setUpdatedAt(LocalDateTime.now());
            usersRepository.save(user);

            return ResponseEntity.ok("User status updated to " + newStatusOpt.get().getName());

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    /* =====================================================
       ADMIN/GENERAL: UPDATE USER STATUS (duplicate legacy route)
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
            /**
             * SQL inside userService.getUserById(id) (legacy/global):
             *   SELECT * FROM users WHERE user_id=:id LIMIT 1;
             */
            Users user = userService.getUserById(id);

            if ("INACTIVE".equalsIgnoreCase(newStatus)) {
                if (password == null || password.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Password is required to deactivate account."));
                }

                // No SQL: password hash compare
                boolean isValid = userService.checkPassword(user, password);
                if (!isValid) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Incorrect password. Status not changed."));
                }
            }

            /**
             * SQL inside userService.getStatus(newStatus):
             *   SELECT * FROM user_status WHERE name=:NEWSTATUS_UPPER LIMIT 1;
             */
            UserStatus statusEntity = userService.getStatus(newStatus);

            // UPDATE users status
            user.setStatus(statusEntity);
            user.setUpdatedAt(LocalDateTime.now());

            /**
             * SQL inside userService.save(user):
             *   UPDATE users SET status=:statusId, updated_at=:now WHERE user_id=:id;
             */
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

            // No SQL: parse JWT
            String email = jwtUtil.extractUsername(jwt);

            /**
             * SQL inside userService.getUserByEmaill(email) (legacy/global):
             *   SELECT * FROM users WHERE email=:email LIMIT 1;
             *   OR if not email -> phone path
             */
            Users user = userService.getUserByEmaill(email);

            // No SQL: just checks in memory
            if (user == null || user.getGoogleId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Only Google users can use this endpoint"));
            }

            String newStatus = requestBody.get("status");
            if (newStatus == null || newStatus.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing status"));
            }

            /**
             * SQL inside userService.getStatus(newStatus):
             *   SELECT * FROM user_status WHERE name=:NEWSTATUS_UPPER LIMIT 1;
             */
            UserStatus status = userService.getStatus(newStatus);

            user.setStatus(status);
            user.setUpdatedAt(LocalDateTime.now());

            /**
             * SQL:
             *   UPDATE users SET status=:statusId, updated_at=:now WHERE user_id=:id;
             */
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
       tenant-scoped by ownerProjectLinkId
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

            String jwt = token.substring(7).trim();

           
            Long userId = jwtUtil.extractId(jwt);
            if (userId == null || userId <= 0) {
                return ResponseEntity.status(401).body("Invalid token: missing user id");
            }

            // ✅ tenant scoped by id + ownerProjectLinkId
            Users user = usersRepository.findByIdAndOwnerProject_Id(userId, ownerProjectLinkId)
                    .orElse(null);

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
            /**
             * SQL inside userService.suggestFriendsByCategory(userId) (current implementation):
             * 1) SELECT * FROM users WHERE user_id=:userId LIMIT 1;
             * 2) SELECT * FROM UserCategories WHERE user_id=:userId;
             * 3) SELECT * FROM UserCategories WHERE category_id IN (:ids);
             * Then Java filters distinct users.
             */
            var suggestions = userService.suggestFriendsByCategory(userId);

            // DTO mapping in memory
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
        /**
         * SQL inside userService.addUserCategories(userId, categoryIds):
         * 1) SELECT * FROM users WHERE user_id=:userId LIMIT 1;
         * 2) SELECT * FROM categories WHERE category_id IN (:categoryIds);
         * 3) For each category:
         *    - SELECT EXISTS(SELECT 1 FROM UserCategories WHERE user_id=? AND category_id=?)
         *    - INSERT INTO UserCategories (user_id, category_id, ...) VALUES (...)
         */
        userService.addUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories added successfully"));
    }

    @GetMapping("/{userId}/categories")
    public ResponseEntity<?> getUserCategories(@PathVariable Long userId) {
        try {
            /**
             * SQL inside userService.getUserCategories(userId):
             * 1) SELECT * FROM users WHERE user_id=:userId LIMIT 1;
             * 2) SELECT * FROM UserCategories WHERE user_id=:userId;
             * (plus potential extra SELECTs if Category is lazy and not already loaded)
             */
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

            /**
             * SQL inside userService.updateUserCategory(userId, categoryId, newCategoryName):
             * - SELECT user
             * - SELECT old category by id
             * - SELECT new category by name (ignore case)
             * - EXISTS old mapping
             * - DELETE old mapping
             * - EXISTS new mapping
             * - INSERT new mapping (if missing)
             */
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
            /**
             * SQL inside userService.deleteUserCategory(userId, categoryId):
             * - SELECT user
             * - SELECT category
             * - EXISTS mapping
             * - DELETE mapping
             */
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
        /**
         * SQL inside userService.replaceUserCategories(userId, categoryIds):
         * - SELECT user
         * - SELECT existing mappings
         * - DELETE ALL existing mappings for user
         * - For each new category id:
         *    SELECT category
         *    EXISTS mapping
         *    INSERT mapping (if missing)
         */
        userService.replaceUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories replaced successfully"));
    }

    /* =====================================================
       DELETE PROFILE IMAGE (legacy/global)
       ===================================================== */
    @DeleteMapping("/delete-profile-image/{id}")
    public ResponseEntity<?> deleteProfileImage(@PathVariable Long id) {
        /**
         * SQL inside userService.deleteUserProfileImage(id):
         * 1) SELECT * FROM users WHERE user_id=:id LIMIT 1;
         * 2) UPDATE users SET profile_picture_url=NULL, updated_at=:now WHERE user_id=:id;
         * (plus file delete in filesystem - not SQL)
         */
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
        /**
         * SQL inside userService.updateVisibilityAndStatus(id, isPublicProfile, status):
         * 1) SELECT * FROM users WHERE user_id=:id LIMIT 1;
         * 2) UPDATE users SET is_public_profile=?, status=?, updated_at=? WHERE user_id=?;
         */
        boolean updated = userService.updateVisibilityAndStatus(id, isPublicProfile, status);

        return updated
                ? ResponseEntity.ok("Visibility and status updated successfully.")
                : ResponseEntity.status(404).body("User not found.");
    }

    /* =====================================================
       COMPAT ROUTE: replaceUserCategories with many legacy paths
       ===================================================== */

    @PostMapping({
        "/{userId}/categoriess",       // legacy typo only
        "/{userId}/UpdateCategory",    // legacy
        "/{userId}/UpdateInterest"     // legacy
    })
    public ResponseEntity<?> replaceUserCategoriesCompat(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds
    ) {
        userService.replaceUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories replaced successfully"));
    }
}
