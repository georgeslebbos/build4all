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
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            /**
             * SQL executed by userService.getUserDtoByIdAndOwnerProject(id, ownerProjectLinkId)
             *
             * In service it calls:
             *   userRepository.fetchByIdAndOwnerProjectId(id, ownerProjectLinkId)
             *
             * JPQL fetch-join translates roughly to:
             *   SELECT u.*, op.*, admin.*, project.*
             *   FROM users u
             *   JOIN admin_user_project op ON u.aup_id = op.id
             *   LEFT JOIN admin_users admin ON op.admin_id = admin.admin_id
             *   LEFT JOIN projects project ON op.project_id = project.id
             *   WHERE u.user_id = :id AND op.id = :ownerProjectLinkId
             *   LIMIT 1;
             *
             * Then DTO mapping is in-memory (no SQL).
             */
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

            // No SQL: claim extraction
            String contact = jwtUtil.extractUsername(token);

            /**
             * SQL (legacy/global lookup - NOT tenant-scoped):
             * 1) usersRepository.findByEmail(contact)
             *    SELECT * FROM users WHERE email = :contact LIMIT 1;
             *
             * 2) if null -> usersRepository.findByPhoneNumber(contact)
             *    SELECT * FROM users WHERE phone_number = :contact LIMIT 1;
             *
             * ⚠️ Risk: in multi-tenant, same email/phone might exist across tenants.
             * Better: require ownerProjectLinkId and use findByEmailAndOwnerProject_Id(...)
             */
            Users acting = usersRepository.findByEmail(contact);
            if (acting == null) acting = usersRepository.findByPhoneNumber(contact);

            String role = jwtUtil.extractRole(token);

            // SUPER_ADMIN can delete any user (global route)
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {

                /**
                 * SQL inside userService.deleteUserById(id):
                 *  - userRepository.findById(id)
                 *      SELECT * FROM users WHERE user_id = :id LIMIT 1;
                 *  - if present -> userRepository.delete(u)
                 *      DELETE FROM users WHERE user_id = :id;
                 */
                boolean deleted = userService.deleteUserById(id);

                return deleted
                        ? ResponseEntity.ok("User deleted by SUPER_ADMIN successfully")
                        : ResponseEntity.status(404).body("User not found");
            }

            // self-delete: must be the same user id
            if (acting == null || !Objects.equals(acting.getId(), id)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            String password = body.get("password");
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Password is required");
            }

            /**
             * SQL inside userService.deleteUserByIdWithPassword(id, password):
             * 1) SELECT user by PK
             *    SELECT * FROM users WHERE user_id=:id LIMIT 1;
             * 2) passwordEncoder.matches(...) -> no SQL
             * 3) if ok -> DELETE user
             *    DELETE FROM users WHERE user_id=:id;
             */
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
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid token");
        }

        // No SQL: JWT parsing
        String contact = jwtUtil.extractUsername(token.substring(7).trim());

        /**
         * SQL (legacy/global; not tenant-scoped):
         *   SELECT * FROM users WHERE email=:contact LIMIT 1;
         *   OR
         *   SELECT * FROM users WHERE phone_number=:contact LIMIT 1;
         *
         * ⚠️ Same tenant ambiguity risk. Prefer tenant-scoped endpoints using ownerProjectLinkId.
         */
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

            // No SQL: passwordEncoder.matches (hash compare)
            if (!userService.checkPassword(user, password))
                return ResponseEntity.status(401).body("Incorrect password. Status not changed.");
        }

        /**
         * SQL:
         *   SELECT * FROM user_status WHERE LOWER(name)=LOWER(:statusStr) LIMIT 1;
         */
        Optional<UserStatus> newStatusOpt = userStatusRepository.findByNameIgnoreCase(statusStr);
        if (newStatusOpt.isEmpty()) return ResponseEntity.badRequest().body("Invalid status value");

        // This becomes UPDATE on users
        user.setStatus(newStatusOpt.get());
        user.setUpdatedAt(LocalDateTime.now());

        /**
         * SQL:
         *   UPDATE users SET status=:statusId, updated_at=:now WHERE user_id=:id;
         */
        usersRepository.save(user);

        return ResponseEntity.ok("User status updated to " + newStatusOpt.get().getName());
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

            // No SQL: JWT claim extraction
            String contact = jwtUtil.extractUsername(token.substring(7).trim());

            /**
             * SQL (tenant-scoped):
             *   SELECT * FROM users
             *   WHERE email=:contact AND aup_id=:ownerProjectLinkId
             *   LIMIT 1;
             */
            Users user = usersRepository.findByEmailAndOwnerProject_Id(contact, ownerProjectLinkId);

            if (user == null) {
                /**
                 * SQL (tenant-scoped):
                 *   SELECT * FROM users
                 *   WHERE phone_number=:contact AND aup_id=:ownerProjectLinkId
                 *   LIMIT 1;
                 */
                user = usersRepository.findByPhoneNumberAndOwnerProject_Id(contact, ownerProjectLinkId);
            }

            if (user == null) {
                return ResponseEntity.status(404).body("User not found in this app");
            }

            // UPDATE
            user.setIsPublicProfile(isPublic);

            /**
             * SQL:
             *   UPDATE users SET is_public_profile=:isPublic WHERE user_id=:id;
             */
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
        // SQL: same as replaceUserCategories(...) above.
        userService.replaceUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories replaced successfully"));
    }
}
