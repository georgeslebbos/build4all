package com.build4all.user.web;

import com.build4all.common.errors.ApiException;
import com.build4all.security.JwtUtil;
import com.build4all.security.service.AuthTokenRevocationService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UsersController (secured)
 *
 * What this controller enforces:
 * 1) Authentication: must provide Bearer JWT for protected endpoints.
 * 2) Authorization (roles): @PreAuthorize gates which roles can enter.
 * 3) Object-level authorization (IDOR/BOLA protection):
 *    - USER can only act on their own userId (self-check).
 *    - OWNER/MANAGER can act within the same tenant (tenant-check).
 *    - SUPER_ADMIN can bypass tenant-check (global).
 *
 * Response style:
 * - success => { "message": "...", ...optional }
 * - error   => { "error": "...", ...optional }
 */
@RestController
@RequestMapping("/api/users")
public class UsersController {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsersRepository usersRepository;
    @Autowired private UserStatusRepository userStatusRepository;
    @Autowired private AuthTokenRevocationService tokenRevocationService;
    
    private final UserService userService;
    
    public UsersController(UserService userService) { this.userService = userService; }

    /* =====================================================
       Helpers (JWT / roles / tenant / self checks)
       ===================================================== */

  
    

    private String roleOf(String jwt) {
        return Optional.ofNullable(jwtUtil.extractRole(jwt)).orElse("").trim().toUpperCase();
    }

    private boolean isSuperAdmin(String role) {
        return "SUPER_ADMIN".equalsIgnoreCase(role);
    }

    /**
     * Tenant check:
     * - For most roles, require ownerProjectLinkId in token == request param ownerProjectLinkId.
     * - SUPER_ADMIN can bypass (global).
     */
    private String requireBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Missing or invalid token");
        }
        return authHeader.substring(7).trim();
    }

    private void requireValidJwt(String jwt) {
        if (!jwtUtil.validateToken(jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Invalid or expired token");
        }
    }

    private void requireTenantUnlessSuperAdmin(String jwt, String role, Long ownerProjectLinkId) {
        if ("SUPER_ADMIN".equalsIgnoreCase(role)) return;
        try {
            jwtUtil.requireTenantMatch(jwt, ownerProjectLinkId);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH", "Tenant mismatch");
        }
    }

    private void requireSelfIfUser(String jwt, String role, Long pathUserId) {
        if (!"USER".equalsIgnoreCase(role)) return;

        Long tokenUserId = jwtUtil.extractId(jwt);
        if (tokenUserId == null || tokenUserId <= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Invalid token: missing user id");
        }
        if (!Objects.equals(tokenUserId, pathUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied");
        }
    }
  

    private ResponseEntity<?> ok(String message) {
        return ResponseEntity.ok(new LinkedHashMap<>(Map.of("message", message)));
    }

    private ResponseEntity<?> ok(Map<String, Object> body) {
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<?> err(HttpStatus status, String error) {
        return ResponseEntity.status(status).body(new LinkedHashMap<>(Map.of("error", error)));
    }
    
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String msg) {
        return error(HttpStatus.UNAUTHORIZED, msg);
    }

    private ResponseEntity<Map<String, Object>> forbidden(String msg) {
        return error(HttpStatus.FORBIDDEN, msg);
    }

    private ResponseEntity<Map<String, Object>> authFail(String msg) {
        // same logic as CategoryController
        if (msg != null && msg.equalsIgnoreCase("Forbidden")) return forbidden(msg);
        return unauthorized(msg);
    }

    private Long tenantFromAuth(String authHeader) {
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    private ResponseEntity<?> asResponse(ApiException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        if (e.getCode() != null) body.put("code", e.getCode());
        if (e.getDetails() != null) body.put("details", e.getDetails());
        return ResponseEntity.status(e.getStatus()).body(body);
    }
    
    
    private void revokeUserTokensIfNeeded(Users user) {
        if (user == null || user.getStatus() == null || user.getStatus().getName() == null) return;

        String status = user.getStatus().getName().trim().toUpperCase();
        if ("ACTIVE".equals(status)) return;

        Long ownerProjectId = user.getOwnerProject() != null ? user.getOwnerProject().getId() : null;
        tokenRevocationService.revokeNow("USER", user.getId(), ownerProjectId);
    }

    /* =====================================================
       1) LIST ALL USERS (tenant-scoped)
       Roles: OWNER, MANAGER, SUPER_ADMIN
       ===================================================== */
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectLinkId = tenantFromAuth(auth);

            String jwt = auth.substring(7).trim();
            String role = Optional.ofNullable(jwtUtil.extractRole(jwt)).orElse("").toUpperCase();

            // Decide who can list users (you had BUSINESS+SUPER_ADMIN+USER)
            if (!role.equals("SUPER_ADMIN") && !role.equals("USER") && !role.equals("BUSINESS")
                    && !role.equals("OWNER") && !role.equals("MANAGER")) {
                return forbidden("Access denied");
            }

            return ResponseEntity.ok(userService.getAllUserDtos(ownerProjectLinkId));

        } catch (IllegalArgumentException e) {
            return authFail(e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load users");
        }
    }

    /* =====================================================
       2) GET ONE USER (tenant-scoped)
       Roles: USER (self), OWNER, MANAGER, SUPER_ADMIN
       ===================================================== */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectLinkId = tenantFromAuth(auth);

            String jwt = auth.substring(7).trim();
            String role = Optional.ofNullable(jwtUtil.extractRole(jwt)).orElse("").toUpperCase();

            // Object-level rules (IDOR fix)
            switch (role) {
                case "SUPER_ADMIN":
                case "OWNER":
                case "MANAGER":
                    // allowed
                    break;

                case "USER":
                    Long tokenUserId = jwtUtil.extractId(jwt);
                    if (!Objects.equals(tokenUserId, id)) return forbidden("Access denied");
                    break;

                default:
                    return forbidden("Access denied");
            }

            UserDto dto = userService.getUserDtoByIdAndOwnerProject(id, ownerProjectLinkId);
            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            // avoid tenant leaks -> return 404 optionally
            return error(HttpStatus.NOT_FOUND, "User not found");
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    /* =====================================================
       3) DELETE USER
       Roles: USER (self) or SUPER_ADMIN
       ===================================================== */
    @PreAuthorize("hasAnyRole('USER','SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            String role = roleOf(jwt);

            if (isSuperAdmin(role)) {
                boolean deleted = userService.deleteUserById(id);
                return deleted ? ok("User deleted by SUPER_ADMIN successfully")
                               : err(HttpStatus.NOT_FOUND, "User not found");
            }

            // USER self delete
            requireSelfIfUser(jwt, role, id);

            String password = body.get("password");
            if (password == null || password.isBlank()) {
                return err(HttpStatus.BAD_REQUEST, "Password is required");
            }

            boolean deleted = userService.deleteUserByIdWithPassword(id, password);

            return deleted ? ok("User deleted successfully")
                           : err(HttpStatus.FORBIDDEN, "Invalid password or user not found");

        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    /* =====================================================
       4) PASSWORD RESET FLOW (public)
       No JWT required by design.
       ===================================================== */

    @PostMapping("/reset-password")
    public ResponseEntity<?> sendResetCode(
            @RequestBody Map<String, String> body,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            String email = body.get("email");
            boolean ok = userService.resetPassword(email, ownerProjectLinkId);

            return ok ? ok("Reset code sent")
                      : err(HttpStatus.NOT_FOUND, "User not found");
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(
            @RequestBody Map<String, String> body,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            String email = body.get("email");
            String code  = body.get("code");

            boolean ok = userService.verifyResetCode(email, code, ownerProjectLinkId);

            return ok ? ok("Code verified successfully")
                      : err(HttpStatus.BAD_REQUEST, "Invalid code");
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @PostMapping("/update-password")
    public ResponseEntity<?> updatePassword(
            @RequestBody Map<String, String> body,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            String email = body.get("email");
            String code  = body.get("code");
            String newPassword = body.get("newPassword");

            if (newPassword == null || newPassword.isBlank()) {
                return err(HttpStatus.BAD_REQUEST, "New password is required");
            }

            boolean ok = userService.updatePassword(email, code, newPassword, ownerProjectLinkId);

            return ok ? ok("Password updated successfully")
                      : err(HttpStatus.BAD_REQUEST, "Invalid code or user");
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    /* =====================================================
       5) UPDATE USER PROFILE
       Roles: USER (self), SUPER_ADMIN
       Tenant scoped for USER; SUPER_ADMIN bypasses tenant match if desired.
       ===================================================== */
    @PutMapping(value = "/{id}/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateUserProfile(
            @PathVariable Long id,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean isPublicProfile,
            @RequestParam(required = false) Boolean imageRemoved,
            @RequestPart(required = false) MultipartFile profileImage,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectLinkId = tenantFromAuth(auth);

            String jwt = auth.substring(7).trim();
            String role = Optional.ofNullable(jwtUtil.extractRole(jwt)).orElse("").toUpperCase();

            // only self user OR admin roles
            if ("USER".equals(role)) {
                Long tokenUserId = jwtUtil.extractId(jwt);
                if (!Objects.equals(tokenUserId, id)) return forbidden("Access denied");
            } else if (!role.equals("SUPER_ADMIN") && !role.equals("OWNER") && !role.equals("MANAGER")) {
                return forbidden("Access denied");
            }

            Long tokenUserId = jwtUtil.extractId(jwt);

            Users updated = userService.updateUserProfile(
                    id, ownerProjectLinkId, tokenUserId,
                    username, firstName, lastName,
                    isPublicProfile, profileImage, imageRemoved
            );

            boolean emailVerificationRequired = false;
            if (email != null && !email.isBlank()) {
                userService.requestEmailChange(id, ownerProjectLinkId, tokenUserId, email);
                emailVerificationRequired = true;
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", emailVerificationRequired
                    ? "Profile updated. Verification code sent to new email."
                    : "Profile updated successfully");
            resp.put("emailVerificationRequired", emailVerificationRequired);
            resp.put("user", new UserDto(updated));
            if (emailVerificationRequired) resp.put("pendingEmail", email.trim());

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            return authFail(e.getMessage());
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update profile");
        }
    }
    /* =====================================================
       6) EMAIL CHANGE VERIFY/RESEND (self)
       Role: USER only
       ===================================================== */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/email-change/verify")
    public ResponseEntity<?> verifyEmailChange(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            Long ownerProjectLinkId = jwtUtil.requireOwnerProjectId(authHeader); // ✅ extract from token/header

            String role = roleOf(jwt);
            requireSelfIfUser(jwt, role, id);

            String code = body.get("code");
            userService.verifyEmailChange(id, ownerProjectLinkId, jwtUtil.extractId(jwt), code);

            return ok("Email updated successfully");

        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }
    
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/email-change/resend")
    public ResponseEntity<?> resendEmailChange(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            // 1) Auth
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            // 2) Tenant from token (NO client param)
            Long ownerProjectLinkId = jwtUtil.requireOwnerProjectId(authHeader);

            // 3) Object-level auth (self only)
            String role = roleOf(jwt);
            requireSelfIfUser(jwt, role, id);

            // 4) Action
            Long tokenUserId = jwtUtil.extractId(jwt);
            userService.resendEmailChangeCode(id, ownerProjectLinkId, tokenUserId);

            return ok("Verification code resent");

        } catch (ApiException e) {
            return asResponse(e);
        } catch (IllegalArgumentException e) {
            // if requireOwnerProjectId throws IllegalArgumentException
            return err(HttpStatus.UNAUTHORIZED, e.getMessage() == null ? "Unauthorized" : e.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }
    
    
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/phone-change/request")
    public ResponseEntity<?> requestPhoneChange(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            Long ownerProjectLinkId = jwtUtil.requireOwnerProjectId(authHeader);

            String role = roleOf(jwt);
            requireSelfIfUser(jwt, role, id);

            Long tokenUserId = jwtUtil.extractId(jwt);
            String newPhone = body.get("newPhone");

            userService.requestPhoneChange(id, ownerProjectLinkId, tokenUserId, newPhone);

            return ok("Verification code sent to new phone number");

        } catch (ApiException e) {
            return asResponse(e);
        } catch (IllegalArgumentException e) {
            return err(HttpStatus.UNAUTHORIZED, e.getMessage() == null ? "Unauthorized" : e.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/phone-change/verify")
    public ResponseEntity<?> verifyPhoneChange(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            Long ownerProjectLinkId = jwtUtil.requireOwnerProjectId(authHeader);

            String role = roleOf(jwt);
            requireSelfIfUser(jwt, role, id);

            String code = body.get("code");
            userService.verifyPhoneChange(id, ownerProjectLinkId, jwtUtil.extractId(jwt), code);

            return ok("Phone number updated successfully");

        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/phone-change/resend")
    public ResponseEntity<?> resendPhoneChange(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            Long ownerProjectLinkId = jwtUtil.requireOwnerProjectId(authHeader);

            String role = roleOf(jwt);
            requireSelfIfUser(jwt, role, id);

            Long tokenUserId = jwtUtil.extractId(jwt);
            userService.resendPhoneChangeCode(id, ownerProjectLinkId, tokenUserId);

            return ok("Verification code resent");

        } catch (ApiException e) {
            return asResponse(e);
        } catch (IllegalArgumentException e) {
            return err(HttpStatus.UNAUTHORIZED, e.getMessage() == null ? "Unauthorized" : e.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    /* =====================================================
       7) SELF STATUS UPDATE (self)
       Role: USER only
       ===================================================== */
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            String role = roleOf(jwt);
            requireSelfIfUser(jwt, role, id);

            Users user = usersRepository.findById(id).orElse(null);
            if (user == null) return err(HttpStatus.NOT_FOUND, "User not found");

            String statusStr = body.getOrDefault("status", "").trim();
            String password  = body.get("password");

            if (statusStr.isBlank()) return err(HttpStatus.BAD_REQUEST, "Status is required");

            if ("INACTIVE".equalsIgnoreCase(statusStr)) {
                if (password == null || password.isBlank()) {
                    return err(HttpStatus.BAD_REQUEST, "Password is required to deactivate account.");
                }
                if (!userService.checkPassword(user, password)) {
                    return err(HttpStatus.UNAUTHORIZED, "Incorrect password. Status not changed.");
                }
            }

            Optional<UserStatus> newStatusOpt = userStatusRepository.findByNameIgnoreCase(statusStr);
            if (newStatusOpt.isEmpty()) return err(HttpStatus.BAD_REQUEST, "Invalid status value");

            user.setStatus(newStatusOpt.get());
            user.setUpdatedAt(LocalDateTime.now());
            usersRepository.save(user);
            revokeUserTokensIfNeeded(user);

            return ok("User status updated to " + newStatusOpt.get().getName());

        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    /* =====================================================
       8) ADMIN: UPDATE USER STATUS (legacy route)
       Role: SUPER_ADMIN only
       ===================================================== */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatusAdmin(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody
    ) {
        try {
            String newStatus = requestBody.get("status");
            String password  = requestBody.get("password");

            if (newStatus == null || newStatus.isBlank()) {
                return err(HttpStatus.BAD_REQUEST, "Missing status");
            }

            Users user = userService.getUserById(id);

            if ("INACTIVE".equalsIgnoreCase(newStatus)) {
                if (password == null || password.isBlank()) {
                    return err(HttpStatus.BAD_REQUEST, "Password is required to deactivate account.");
                }
                if (!userService.checkPassword(user, password)) {
                    return err(HttpStatus.UNAUTHORIZED, "Incorrect password. Status not changed.");
                }
            }

            UserStatus statusEntity = userService.getStatus(newStatus);

            user.setStatus(statusEntity);
            user.setUpdatedAt(LocalDateTime.now());
            userService.save(user);
            revokeUserTokensIfNeeded(user);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "User status updated successfully");
            resp.put("newStatus", newStatus.toUpperCase());
            return ok(resp);

        } catch (RuntimeException e) {
            return err(HttpStatus.BAD_REQUEST, e.getMessage() == null ? "Bad request" : e.getMessage());
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");
        }
    }

    /* =====================================================
       9) GOOGLE USER STATUS (self)
       Role: USER only
       ===================================================== */
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/auth/google/status")
    public ResponseEntity<?> updateGoogleUserStatus(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestBody Map<String, String> requestBody
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            // Prefer userId claim (stronger than email)
            Long userId = jwtUtil.extractId(jwt);
            if (userId == null || userId <= 0) {
                return err(HttpStatus.UNAUTHORIZED, "Invalid token: missing user id");
            }

            Users user = usersRepository.findById(userId).orElse(null);
            if (user == null) return err(HttpStatus.NOT_FOUND, "User not found");

            if (user.getGoogleId() == null) {
                return err(HttpStatus.FORBIDDEN, "Only Google users can use this endpoint");
            }

            String newStatus = requestBody.get("status");
            if (newStatus == null || newStatus.isBlank()) {
                return err(HttpStatus.BAD_REQUEST, "Missing status");
            }

            UserStatus status = userService.getStatus(newStatus);
            user.setStatus(status);
            user.setUpdatedAt(LocalDateTime.now());
            userService.save(user);
            revokeUserTokensIfNeeded(user);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Google account status updated");
            resp.put("status", status.getName());
            resp.put("googleId", user.getGoogleId());
            return ok(resp);

        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update Google user status");
        }
    }

    /* =====================================================
       10) PROFILE VISIBILITY (self)
       Role: USER only
       ===================================================== */
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/profile-visibility")
    @PatchMapping("/profile-visibility")
    public ResponseEntity<?> updateProfileVisibility(
            @RequestParam boolean isPublic,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            Long ownerProjectLinkId = jwtUtil.requireOwnerProjectId(authHeader); // ✅

            Long userId = jwtUtil.extractId(jwt);
            if (userId == null || userId <= 0) {
                return err(HttpStatus.UNAUTHORIZED, "Invalid token: missing user id");
            }

            Users user = usersRepository.findByIdAndOwnerProject_Id(userId, ownerProjectLinkId).orElse(null);
            if (user == null) return err(HttpStatus.NOT_FOUND, "User not found in this app");

            user.setIsPublicProfile(isPublic);
            usersRepository.save(user);

            return ok("Profile visibility updated to " + (isPublic ? "PUBLIC" : "PRIVATE"));

        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    /* =====================================================
       11) FRIEND SUGGESTIONS (self)
       Role: USER only
       ===================================================== */
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{userId}/suggestions")
    public ResponseEntity<?> getFriendSuggestions(
            @PathVariable Long userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            String role = roleOf(jwt);
            requireSelfIfUser(jwt, role, userId);

            var suggestions = userService.suggestFriendsByCategory(userId);
            var result = suggestions.stream().map(UserDto::new).collect(Collectors.toList());
            return ResponseEntity.ok(result);

        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching suggestions");
        }
    }

    /* =====================================================
       12) USER CATEGORIES (self)
       Role: USER only
       ===================================================== */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{userId}/categories")
    public ResponseEntity<?> addUserCategories(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            requireSelfIfUser(jwt, roleOf(jwt), userId);

            userService.addUserCategories(userId, categoryIds);
            return ok("User categories added successfully");
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add categories");
        }
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{userId}/categories")
    public ResponseEntity<?> getUserCategories(
            @PathVariable Long userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            requireSelfIfUser(jwt, roleOf(jwt), userId);

            List<String> categories = userService.getUserCategories(userId);
            return ResponseEntity.ok(categories);
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch categories");
        }
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> updateUserCategory(
            @PathVariable Long userId,
            @PathVariable Long categoryId,
            @RequestBody Map<String, String> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            requireSelfIfUser(jwt, roleOf(jwt), userId);

            String newCategoryName = body.get("name");
            if (newCategoryName == null || newCategoryName.isBlank()) {
                return err(HttpStatus.BAD_REQUEST, "Category name is required");
            }

            boolean updated = userService.updateUserCategory(userId, categoryId, newCategoryName);

            return updated ? ok("Category updated successfully")
                           : err(HttpStatus.NOT_FOUND, "Category not found for this user");
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating category");
        }
    }

    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> deleteUserCategory(
            @PathVariable Long userId,
            @PathVariable Long categoryId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            requireSelfIfUser(jwt, roleOf(jwt), userId);

            boolean deleted = userService.deleteUserCategory(userId, categoryId);

            return deleted ? ok("Category removed successfully")
                           : err(HttpStatus.NOT_FOUND, "Category not found for this user");
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete category");
        }
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{userId}/update-category")
    public ResponseEntity<?> replaceUserCategories(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            requireSelfIfUser(jwt, roleOf(jwt), userId);

            userService.replaceUserCategories(userId, categoryIds);
            return ok("User categories replaced successfully");
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to replace categories");
        }
    }

    /* =====================================================
       13) DELETE PROFILE IMAGE
       Roles: USER (self) or SUPER_ADMIN
       ===================================================== */
    @PreAuthorize("hasAnyRole('USER','SUPER_ADMIN')")
    @DeleteMapping("/delete-profile-image/{id}")
    public ResponseEntity<?> deleteProfileImage(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            String role = roleOf(jwt);
            if (!isSuperAdmin(role)) {
                requireSelfIfUser(jwt, role, id);
            }

            boolean success = userService.deleteUserProfileImage(id);

            return success ? ok("Profile image deleted successfully")
                           : err(HttpStatus.NOT_FOUND, "No profile image found or already deleted");
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    /* =====================================================
       14) VISIBILITY + STATUS (legacy/global)
       Role: SUPER_ADMIN only (too risky otherwise)
       ===================================================== */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/visibility-status")
    public ResponseEntity<?> updateVisibilityAndStatus(
            @PathVariable Long id,
            @RequestParam boolean isPublicProfile,
            @RequestParam UserStatus status
    ) {
        try {
            boolean updated = userService.updateVisibilityAndStatus(id, isPublicProfile, status);

            return updated ? ok("Visibility and status updated successfully.")
                           : err(HttpStatus.NOT_FOUND, "User not found.");
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    /* =====================================================
       15) COMPAT ROUTE (legacy aliases) - self only
       Role: USER only
       ===================================================== */
    @PreAuthorize("hasRole('USER')")
    @PostMapping({
            "/{userId}/categoriess",
            "/{userId}/UpdateCategory",
            "/{userId}/UpdateInterest"
    })
    public ResponseEntity<?> replaceUserCategoriesCompat(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        try {
            String jwt = requireBearer(authHeader);
            requireValidJwt(jwt);

            requireSelfIfUser(jwt, roleOf(jwt), userId);

            userService.replaceUserCategories(userId, categoryIds);
            return ok("User categories replaced successfully");
        } catch (ApiException e) {
            return asResponse(e);
        } catch (Exception e) {
            return err(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to replace categories");
        }
    }
}