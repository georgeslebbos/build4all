// src/main/java/com/build4all/authentication/web/AuthController.java
package com.build4all.authentication.web;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.authentication.dto.AdminLoginRequest;
import com.build4all.admin.dto.AdminRegisterRequest;

import com.build4all.admin.service.AdminUserService;
import com.build4all.authentication.service.GoogleAuthService;
import com.build4all.authentication.service.OwnerOtpService;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import com.build4all.business.repository.BusinessStatusRepository;
import com.build4all.business.repository.BusinessesRepository;
import com.build4all.business.service.BusinessService;

import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;

import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;

import com.build4all.security.JwtUtil;

import com.build4all.user.domain.UserStatus;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UserStatusRepository;
import com.build4all.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Authentication / Registration endpoints for:
 * - Users (tenant-aware via ownerProjectLinkId)
 * - Businesses (tenant-aware via ownerProjectLinkId)
 * - Admin roles (SUPER_ADMIN / OWNER / MANAGER)
 *
 * Includes a real OTP-based Owner (AdminUser with role OWNER) email signup flow:
 *   1) /owner/send-verification-email  -> sends one-time code to email
 *   2) /owner/verify-email-code        -> verifies code, returns short-lived registration token
 *   3) /owner/complete-profile         -> creates the OWNER admin using the registration token
 *
 * Also exposes unified admin login that accepts any of SUPER_ADMIN / OWNER / MANAGER.
 *
 * ✅ Multi-tenant security:
 * - USER tokens MUST include ownerProjectId (tenant scope).
 * - BUSINESS tokens MUST include ownerProjectId (tenant scope).
 * - OWNER tokens SHOULD include ownerProjectId (tenant scope via AdminUserProject aup_id).
 * - SUPER_ADMIN tokens may omit ownerProjectId (global).
 *
 * ✅ With the new strict JwtUtil:
 * - Any attempt to mint a USER token without ownerProjectId will throw.
 * - Any attempt to mint a USER token for a different tenant than DB will throw.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Owner-linked user auth, business auth, manager & super admin auth")
public class AuthController {

    /* ========= Services & repos ========= */
    @Autowired private UserService userService;
    @Autowired private BusinessService businessService;
    @Autowired private AdminUserService adminUserService;

    @Autowired private RoleRepository roleRepository;

    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private GoogleAuthService googleAuthService;

    @Autowired private UserStatusRepository userStatusRepository;

    @Autowired private BusinessesRepository businessRepository;
    @Autowired private BusinessStatusRepository businessStatusRepository;

    // Real OTP generator/validator for Owner email signup
    @Autowired private OwnerOtpService ownerOtpService;

    // AdminUserProject (AUP) link repository (tenant scope)
    @Autowired private AdminUserProjectRepository adminUserProjectRepo;

    // ✅ Needed only for OWNER signup to create the first AdminUserProject row
    // because AdminUserProject.project is non-nullable in your entity.
    @Autowired private ProjectRepository projectRepository;

    /* =====================================================================================
     *  USER REGISTRATION (OWNER-LINKED / MULTI-TENANT)
     *  Steps: send-verification -> verify email/phone -> complete-profile
     * ===================================================================================== */

    @PostMapping(value = "/send-verification")
    public ResponseEntity<?> sendVerification(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam("password") String password,
            @RequestParam("ownerProjectLinkId") Long ownerProjectLinkId
    ) {
        try {
            if (ownerProjectLinkId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "ownerProjectLinkId is required"));
            }
            Map<String, String> data = new HashMap<>();
            if (email != null && !email.isBlank()) data.put("email", email.trim());
            if (phoneNumber != null && !phoneNumber.isBlank()) data.put("phoneNumber", phoneNumber.trim());
            data.put("password", password);

            userService.sendVerificationCodeForRegistration(data, ownerProjectLinkId);
            return ResponseEntity.ok(Map.of("message", "Verification code sent"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<?> verifyEmailCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code  = request.get("code");
            Long userId  = userService.verifyEmailCodeAndRegister(email, code);

            return ResponseEntity.ok(Map.of(
                    "message", "Verification successful. Continue with profile setup.",
                    "user", Map.of("id", userId)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/user/verify-phone-code")
    public ResponseEntity<?> verifyUserPhoneCode(@RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phoneNumber");
            String code  = request.get("code");
            Long userId  = userService.verifyPhoneCodeAndRegister(phone, code);

            return ResponseEntity.ok(Map.of(
                    "message", "Phone verification successful. Continue with profile setup.",
                    "user", Map.of("id", userId)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/complete-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> completeProfile(
            @RequestParam Long pendingId,
            @RequestParam String username,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam Boolean isPublicProfile,
            @RequestParam Long ownerProjectLinkId,
            @RequestPart(required = false) MultipartFile profileImage
    ) {
        try {
            if (pendingId == null || username == null || firstName == null || lastName == null
                    || isPublicProfile == null || ownerProjectLinkId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            // Per-tenant username uniqueness
            if (userService.findByUsername(username, ownerProjectLinkId) != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already in use in this app"));
            }

            boolean updated = userService.completeUserProfile(
                    pendingId, username, firstName, lastName, profileImage, isPublicProfile, ownerProjectLinkId
            );
            if (!updated) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Profile update failed"));
            }

            Users user = userService.findByUsername(username, ownerProjectLinkId);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }

            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("firstName", user.getFirstName());
            userData.put("lastName", user.getLastName());
            userData.put("email", user.getEmail());
            userData.put("phoneNumber", user.getPhoneNumber());
            userData.put("profilePictureUrl", user.getProfilePictureUrl());
            userData.put("status", user.getStatus());
            userData.put("isPublicProfile", user.isPublicProfile());
            userData.put("ownerProjectLinkId", user.getOwnerProject() != null ? user.getOwnerProject().getId() : null);

            return ResponseEntity.ok(Map.of(
                    "message", "Profile completed successfully.",
                    "user", userData
            ));
        } catch (BadRequestException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    /* =====================================================================================
     *  USER LOGIN (OWNER-LINKED / TENANT-AWARE)
     * ===================================================================================== */

    @PostMapping("/user/login")
    public ResponseEntity<?> userLogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        Long ownerProjectLinkId = toLongOrNull(body.get("ownerProjectLinkId"));

        if (email == null || password == null || ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email, password and ownerProjectLinkId are required"));
        }

        Users existingUser = userService.findByEmail(email, ownerProjectLinkId);
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        if (!passwordEncoder.matches(password, existingUser.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
        }

        String status = existingUser.getStatus().getName();
        if ("DELETED".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This account has been deleted and cannot be accessed."));
        }

        if ("INACTIVE".equalsIgnoreCase(status)) {
            // ✅ IMPORTANT: with strict JwtUtil, USER token MUST include tenant
            String tempToken = jwtUtil.generateToken(existingUser, ownerProjectLinkId);

            Map<String, Object> inactiveUserData = new HashMap<>();
            inactiveUserData.put("id", existingUser.getId());
            inactiveUserData.put("username", existingUser.getUsername());
            inactiveUserData.put("firstName", existingUser.getFirstName());
            inactiveUserData.put("lastName", existingUser.getLastName());
            inactiveUserData.put("email", existingUser.getEmail());
            inactiveUserData.put("profilePictureUrl", existingUser.getProfilePictureUrl());
            inactiveUserData.put("ownerProjectLinkId", ownerProjectLinkId);

            return ResponseEntity.ok(Map.of(
                    "wasInactive", true,
                    "message", "Your account is inactive. Confirm reactivation.",
                    "token", tempToken,
                    "user", inactiveUserData,
                    "userType", "user",
                    "ownerProjectLinkId", ownerProjectLinkId
            ));
        }

        existingUser.setLastLogin(LocalDateTime.now());
        userService.save(existingUser);

        // ✅ IMPORTANT: with strict JwtUtil, USER token MUST include tenant
        String token = jwtUtil.generateToken(existingUser, ownerProjectLinkId);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", existingUser.getId());
        userData.put("username", existingUser.getUsername());
        userData.put("firstName", existingUser.getFirstName());
        userData.put("lastName", existingUser.getLastName());
        userData.put("email", existingUser.getEmail());
        userData.put("profilePictureUrl", existingUser.getProfilePictureUrl());
        userData.put("ownerProjectLinkId", ownerProjectLinkId);

        return ResponseEntity.ok(Map.of(
                "message", "User login successful",
                "token", token,
                "user", userData,
                "wasInactive", false
        ));
    }

    @PostMapping("/user/login-phone")
    public ResponseEntity<?> userLoginWithPhone(@RequestBody Map<String, String> body) {
        String phone = body.get("phoneNumber");
        String rawPassword = body.get("password");
        Long ownerProjectLinkId = toLongOrNull(body.get("ownerProjectLinkId"));

        if (phone == null || rawPassword == null || ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber, password and ownerProjectLinkId are required"));
        }

        Users existingUser = userService.findByPhoneNumber(phone, ownerProjectLinkId);
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found with this phone number"));
        }

        if (!passwordEncoder.matches(rawPassword, existingUser.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
        }

        String currentStatus = existingUser.getStatus().getName();
        if ("DELETED".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This account has been deleted and cannot be accessed."));
        }

        if ("INACTIVE".equalsIgnoreCase(currentStatus)) {
            // ✅ IMPORTANT: with strict JwtUtil, USER token MUST include tenant
            String tempToken = jwtUtil.generateToken(existingUser, ownerProjectLinkId);

            Map<String, Object> inactiveUserData = new HashMap<>();
            inactiveUserData.put("id", existingUser.getId());
            inactiveUserData.put("username", existingUser.getUsername());
            inactiveUserData.put("phoneNumber", existingUser.getPhoneNumber());
            inactiveUserData.put("firstName", existingUser.getFirstName());
            inactiveUserData.put("lastName", existingUser.getLastName());
            inactiveUserData.put("profilePictureUrl", existingUser.getProfilePictureUrl());
            inactiveUserData.put("ownerProjectLinkId", ownerProjectLinkId);

            return ResponseEntity.ok(Map.of(
                    "wasInactive", true,
                    "message", "Your account is inactive. Confirm reactivation.",
                    "token", tempToken,
                    "user", inactiveUserData,
                    "userType", "user",
                    "ownerProjectLinkId", ownerProjectLinkId
            ));
        }

        existingUser.setLastLogin(LocalDateTime.now());
        userService.save(existingUser);

        // ✅ IMPORTANT: with strict JwtUtil, USER token MUST include tenant
        String token = jwtUtil.generateToken(existingUser, ownerProjectLinkId);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", existingUser.getId());
        userData.put("username", existingUser.getUsername());
        userData.put("firstName", existingUser.getFirstName());
        userData.put("lastName", existingUser.getLastName());
        userData.put("phoneNumber", existingUser.getPhoneNumber());
        userData.put("profilePictureUrl", existingUser.getProfilePictureUrl());
        userData.put("ownerProjectLinkId", ownerProjectLinkId);

        return ResponseEntity.ok(Map.of(
                "message", "User login with phone successful",
                "token", token,
                "user", userData,
                "wasInactive", false
        ));
    }

    /* =====================================================================================
     *  USER REACTIVATION
     * ===================================================================================== */
    @PostMapping("/reactivate")
    public ResponseEntity<?> reactivateAccount(@RequestBody Map<String, Object> request) {

        // ✅ With strict JwtUtil, we must mint a tenant-scoped token.
        // So ownerProjectLinkId is REQUIRED here.
        Long userId = toLongOrNull(request.get("id"));
        Long ownerProjectLinkId = toLongOrNull(request.get("ownerProjectLinkId"));

        if (userId == null || ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "id and ownerProjectLinkId are required"));
        }

        Optional<Users> userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        Users user = userOpt.get();

        // ✅ Extra safety: prevent reactivating across tenants
        if (user.getOwnerProject() == null || user.getOwnerProject().getId() == null
                || !ownerProjectLinkId.equals(user.getOwnerProject().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid tenancy"));
        }

        String currentStatus = user.getStatus().getName();
        if (!"INACTIVE".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not inactive"));
        }

        UserStatus activeStatus = userStatusRepository.findByNameIgnoreCase("ACTIVE")
                .orElseThrow(() -> new RuntimeException("Status 'ACTIVE' not found"));

        user.setStatus(activeStatus);
        user.setLastLogin(LocalDateTime.now());
        userService.save(user);

        // ✅ IMPORTANT: with strict JwtUtil, USER token MUST include tenant
        String token = jwtUtil.generateToken(user, ownerProjectLinkId);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("firstName", user.getFirstName());
        userData.put("lastName", user.getLastName());
        userData.put("email", user.getEmail());
        userData.put("profilePictureUrl", user.getProfilePictureUrl());
        userData.put("ownerProjectLinkId", ownerProjectLinkId);

        return ResponseEntity.ok(Map.of(
                "message", "Account reactivated successfully",
                "token", token,
                "user", userData
        ));
    }

    /* =====================================================================================
     *  BUSINESS REGISTRATION & LOGIN (TENANT-AWARE)
     * ===================================================================================== */

    @PostMapping("/business/send-verification")
    public ResponseEntity<?> sendBusinessVerification(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam String password,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {
            if (ownerProjectLinkId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "ownerProjectLinkId is required"));
            }
            Map<String, String> businessData = new HashMap<>();
            if (email != null && !email.isBlank()) businessData.put("email", email.trim());
            if (phoneNumber != null && !phoneNumber.isBlank()) businessData.put("phoneNumber", phoneNumber.trim());
            businessData.put("password", password);
            businessData.put("status", "ACTIVE");

            Long pendingId = businessService.sendBusinessVerificationCode(ownerProjectLinkId, businessData);

            return ResponseEntity.ok(Map.of(
                    "pendingId", pendingId,
                    "message", phoneNumber != null ? "Static code 123456 set for phone verification"
                            : "Verification code sent to email"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    @PostMapping("/business/verify-code")
    public ResponseEntity<?> verifyBusinessCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code  = request.get("code");
            Long pendingId = businessService.verifyBusinessEmailCode(email, code);

            return ResponseEntity.ok(Map.of("business", Map.of("id", pendingId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/business/verify-phone-code")
    public ResponseEntity<?> verifyBusinessPhoneCode(@RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");
            String code        = request.get("code");
            Long pendingId     = businessService.verifyBusinessPhoneCode(phoneNumber, code);

            return ResponseEntity.ok(Map.of("business", Map.of("id", pendingId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/business/complete-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> completeBusinessProfile(
            @RequestParam Long ownerProjectLinkId,
            @RequestParam Long pendingId,
            @RequestParam String businessName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String websiteUrl,
            @RequestPart(required = false) MultipartFile logo,
            @RequestPart(required = false) MultipartFile banner
    ) {
        try {
            if (ownerProjectLinkId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "ownerProjectLinkId is required"));
            }
            if (pendingId == null || businessName == null || businessName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "pendingId and businessName are required"));
            }

            Businesses updatedBusiness = businessService.completeBusinessProfile(
                    ownerProjectLinkId, pendingId, businessName.trim(), description, websiteUrl, logo, banner
            );

            Map<String, Object> businessData = baseBusinessMap(updatedBusiness);
            businessData.put("status", updatedBusiness.getStatus());
            businessData.put("isPublicProfile", updatedBusiness.getIsPublicProfile());

            return ResponseEntity.ok(Map.of(
                    "message", "Business profile completed successfully.",
                    "business", businessData
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-business-code")
    public ResponseEntity<?> resendBusinessCode(@RequestBody Map<String, String> request) {
        String contact = request.get("emailOrPhone");
        try {
            businessService.resendBusinessVerificationCode(contact);
            return ResponseEntity.ok(Map.of("message", "Verification code resent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/business/login")
    public ResponseEntity<?> businessLogin(@RequestBody Map<String, String> body) {
        String emailOrPhone = body.get("email"); // may contain email OR phone
        String password = body.get("password");
        Long ownerProjectLinkId = toLongOrNull(body.get("ownerProjectLinkId"));

        if (emailOrPhone == null || password == null || ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email (or phone), password, and ownerProjectLinkId are required"));
        }

        try {
            Businesses business = businessService.findByEmail(ownerProjectLinkId, emailOrPhone);
            if (!passwordEncoder.matches(password, business.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
            }

            String statusName = business.getStatus() != null ? business.getStatus().getName() : "";
            if ("INACTIVEBYADMIN".equalsIgnoreCase(statusName)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Your account has been deactivated by the administrator due to low rating. Please contact support."));
            }
            if ("DELETED".equalsIgnoreCase(statusName)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "This account has been deleted and cannot be accessed."));
            }
            if ("INACTIVE".equalsIgnoreCase(statusName)) {
                // ✅ Business token already embeds ownerProjectId from DB (business.ownerProjectLink.id)
                // (Your JwtUtil.generateToken(Businesses) reads it from the entity.)
                String tempToken = jwtUtil.generateToken(business);

                Map<String, Object> businessData = baseBusinessMap(business);
                businessData.put("userType", "business");
                return ResponseEntity.ok(Map.of(
                        "wasInactive", true,
                        "token", tempToken,
                        "business", businessData
                ));
            }

            business.setLastLoginAt(LocalDateTime.now());
            businessService.save(ownerProjectLinkId, business);

            // ✅ Business token already embeds ownerProjectId from DB
            String token = jwtUtil.generateToken(business);
            Map<String, Object> businessData = baseBusinessMap(business);

            return ResponseEntity.ok(Map.of(
                    "message", "Business login successful",
                    "token", token,
                    "business", businessData,
                    "ownerProjectLinkId", ownerProjectLinkId,
                    "wasInactive", false
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/business/login-phone")
    public ResponseEntity<?> businessLoginWithPhone(@RequestBody Map<String, String> body) {
        String phone = body.get("phoneNumber");
        String rawPassword = body.get("password");
        Long ownerProjectLinkId = toLongOrNull(body.get("ownerProjectLinkId"));

        if (phone == null || rawPassword == null || ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber, password, and ownerProjectLinkId are required"));
        }

        Optional<Businesses> optionalBusiness = businessService.findByEmailOptional(ownerProjectLinkId, phone);
        if (optionalBusiness.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Business not found with this phone number"));
        }
        Businesses business = optionalBusiness.get();

        if (!passwordEncoder.matches(rawPassword, business.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
        }

        String statusName = business.getStatus() != null ? business.getStatus().getName() : "";
        if ("INACTIVEBYADMIN".equalsIgnoreCase(statusName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Your account has been deactivated by the administrator due to low rating. Please contact support."));
        }
        if ("DELETED".equalsIgnoreCase(statusName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This account has been deleted and cannot be accessed."));
        }
        if ("INACTIVE".equalsIgnoreCase(statusName)) {
            // ✅ Business token already embeds ownerProjectId from DB
            String tempToken = jwtUtil.generateToken(business);

            Map<String, Object> businessData = baseBusinessMap(business);
            businessData.put("userType", "business");
            return ResponseEntity.ok(Map.of(
                    "wasInactive", true,
                    "token", tempToken,
                    "business", businessData
            ));
        }

        business.setLastLoginAt(LocalDateTime.now());
        businessService.save(ownerProjectLinkId, business);

        // ✅ Business token already embeds ownerProjectId from DB
        String token = jwtUtil.generateToken(business);
        Map<String, Object> businessData = baseBusinessMap(business);

        return ResponseEntity.ok(Map.of(
                "message", "Business login with phone successful",
                "token", token,
                "business", businessData,
                "wasInactive", false
        ));
    }

    @PostMapping("/business/reactivate")
    public ResponseEntity<?> reactivateBusiness(@RequestBody Map<String, Long> request) {
        Long id = request.get("id");
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        Optional<Businesses> optional = businessRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));
        }

        Businesses business = optional.get();
        if (!"INACTIVE".equalsIgnoreCase(business.getStatus().getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Account is not inactive"));
        }

        BusinessStatus activeStatus = businessStatusRepository.findByNameIgnoreCase("ACTIVE")
                .orElseThrow(() -> new RuntimeException("ACTIVE status not found"));

        business.setStatus(activeStatus);
        businessRepository.save(business);

        // ✅ Business token already embeds ownerProjectId from DB (ownerProjectLink.id)
        String token = jwtUtil.generateToken(business);
        Map<String, Object> businessData = baseBusinessMap(business);

        return ResponseEntity.ok(Map.of(
                "message", "Business login successful",
                "token", token,
                "business", businessData
        ));
    }

    /* =====================================================================================
     *  MANAGER / SUPER_ADMIN / OWNER (ADMIN) AUTH
     * ===================================================================================== */

    @Operation(summary = "Login as a Manager",
            description = "Authenticates a Manager from the AdminUsers table based on email/username and password",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful, JWT token returned"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or not a manager")
            })
    @PostMapping("/manager/login")
    public ResponseEntity<?> managerLogin(@RequestBody AdminLoginRequest request) {
        Optional<AdminUser> optionalAdmin = adminUserService.findByUsernameOrEmail(request.getUsernameOrEmail());
        if (optionalAdmin.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        AdminUser admin = optionalAdmin.get();
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        if (!"MANAGER".equalsIgnoreCase(admin.getRole().getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Access denied: Not a Manager"));
        }

        // If Manager belongs to a business, the business must be active.
        if (admin.getBusiness() != null) {
            Businesses business = admin.getBusiness();
            String status = (business.getStatus() != null) ? business.getStatus().getName().toUpperCase() : "";
            if ("INACTIVE".equals(status) ||
                    "INACTIVEBYADMIN".equals(status) ||
                    "INACTIVEBYBUSINESS".equals(status) ||
                    "DELETED".equals(status)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Cannot log in: This business account is disabled or deleted. Please contact support."));
            }
        }

        // ✅ Manager can be tenant-scoped if it belongs to a business
        // If you want MANAGER to always be tenant-scoped, keep it like this:
        Long ownerProjectId = null;
        if (admin.getBusiness() != null && admin.getBusiness().getOwnerProjectLink() != null) {
            ownerProjectId = admin.getBusiness().getOwnerProjectLink().getId(); // aup_id
        }

        String token = jwtUtil.generateToken(admin, ownerProjectId);

        Map<String, Object> managerData = new HashMap<>();
        managerData.put("id", admin.getAdminId());
        managerData.put("username", admin.getUsername());
        managerData.put("firstName", admin.getFirstName());
        managerData.put("lastName", admin.getLastName());
        managerData.put("email", admin.getEmail());
        managerData.put("role", admin.getRole().getName());

        Map<String, Object> businessData = null;
        if (admin.getBusiness() != null) {
            Businesses b = admin.getBusiness();
            businessData = baseBusinessMap(b);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Manager login successful",
                "token", token,
                "manager", managerData,
                "business", businessData,
                "ownerProjectId", ownerProjectId
        ));
    }

    @Operation(summary = "Login as Super Admin",
            description = "Authenticates a Super Admin using email and password",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful, JWT returned"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or not a Super Admin")
            })
    @PostMapping("/superadmin/login")
    public ResponseEntity<?> superAdminLogin(@RequestBody AdminLoginRequest request) {
        if (request.getUsernameOrEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email and password are required"));
        }

        Optional<AdminUser> adminOpt = adminUserService.findByEmail(request.getUsernameOrEmail());
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No Super Admin found with this email"));
        }

        AdminUser admin = adminOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect password"));
        }

        if (!"SUPER_ADMIN".equalsIgnoreCase(admin.getRole().getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Access denied: Not a Super Admin"));
        }

        // ✅ SUPER_ADMIN is global. ownerProjectId is optional.
        String token = jwtUtil.generateToken(admin, null);

        Map<String, Object> adminData = new HashMap<>();
        adminData.put("id", admin.getAdminId());
        adminData.put("username", admin.getUsername());
        adminData.put("firstName", admin.getFirstName());
        adminData.put("lastName", admin.getLastName());
        adminData.put("email", admin.getEmail());
        adminData.put("role", admin.getRole().getName());

        return ResponseEntity.ok(Map.of(
                "message", "Super Admin login successful",
                "token", token,
                "admin", adminData
        ));
    }

    @Operation(summary = "Register a new Super Admin",
            description = "Registers a new AdminUser with the role SUPER_ADMIN")
    @PostMapping("/admin/register")
    public ResponseEntity<?> registerSuperAdmin(@RequestBody AdminRegisterRequest request) {
        if (adminUserService.findByUsernameOrEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username or email already in use"));
        }

        Role role = roleRepository.findByName("SUPER_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role SUPER_ADMIN not found"));

        AdminUser newAdmin = new AdminUser();
        newAdmin.setUsername(request.getUsername());
        newAdmin.setFirstName(request.getFirstName());
        newAdmin.setLastName(request.getLastName());
        newAdmin.setEmail(request.getEmail());
        newAdmin.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newAdmin.setRole(role);

        adminUserService.save(newAdmin);

        return ResponseEntity.ok(Map.of(
                "message", "Super Admin registered successfully",
                "adminId", newAdmin.getAdminId()
        ));
    }

    @Operation(summary = "Remove a manager",
            description = "Deletes a manager from AdminUsers (direct FK; no join-table now)")
    @ApiResponse(responseCode = "200", description = "Manager removed successfully")
    @ApiResponse(responseCode = "404", description = "Manager not found")
    @DeleteMapping("/admin/remove-manager/{adminId}")
    public ResponseEntity<?> removeManager(@PathVariable Long adminId){
        Optional<AdminUser> optionalManager = adminUserService.findById(adminId);
        if (optionalManager.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Manager not found"));
        }
        adminUserService.deleteManagerById(adminId);
        return ResponseEntity.ok(Map.of("message", "Manager removed successfully"));
    }

    /* ---------- UNIFIED ADMIN LOGIN (SUPER_ADMIN / OWNER / MANAGER) ---------- */
    @Operation(summary = "Unified Admin Login (SUPER_ADMIN / OWNER)")
    @PostMapping("/admin/login/front")
    public ResponseEntity<?> adminLoginFront(@RequestBody AdminLoginRequest request) {

        if (request.getUsernameOrEmail() == null || request.getUsernameOrEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "usernameOrEmail and password are required"));
        }

        var opt = adminUserService.findByUsernameOrEmail(request.getUsernameOrEmail().trim());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        AdminUser admin = opt.get();

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        String role = (admin.getRole() != null) ? admin.getRole().getName() : null;
        if (role == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Access denied for this role"));
        }

        boolean isSuperAdmin = "SUPER_ADMIN".equalsIgnoreCase(role);
        boolean isOwner = "OWNER".equalsIgnoreCase(role);

        // ✅ Allowed roles ONLY
        if (!isSuperAdmin && !isOwner) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Access denied for this role"));
        }

        Long ownerProjectId = null;

        if (isOwner) {
            // OWNER must be tenant-scoped (AUP id)
            List<AdminUserProject> links = adminUserProjectRepo.findByAdmin_AdminId(admin.getAdminId());
            if (links == null || links.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Owner has no AdminUserProject link. Create an app first."));
            }

            Long requestedOwnerProjectId = request.getOwnerProjectId();

            // If frontend requested a specific AUP id, validate it belongs to this owner
            if (requestedOwnerProjectId != null) {
                boolean ok = links.stream().anyMatch(l -> requestedOwnerProjectId.equals(l.getId()));
                if (!ok) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Invalid ownerProjectId for this owner"));
                }
                ownerProjectId = requestedOwnerProjectId;
            } else {
                // Default: pick the first AUP
                ownerProjectId = links.get(0).getId();
            }
        }

        // ✅ SUPER_ADMIN is global -> ownerProjectId stays null
        String token = jwtUtil.generateToken(admin, ownerProjectId);

        Map<String, Object> adminData = new HashMap<>();
        adminData.put("id", admin.getAdminId());
        adminData.put("username", admin.getUsername());
        adminData.put("firstName", admin.getFirstName());
        adminData.put("lastName", admin.getLastName());
        adminData.put("email", admin.getEmail());
        adminData.put("role", role);

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "token", token,
                "role", role,
                "ownerProjectId", ownerProjectId,
                "admin", adminData
        ));
    }

    @Operation(summary = "Unified Admin Login (SUPER_ADMIN / OWNER / MANAGER)")
    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody AdminLoginRequest request) {
        if (request.getUsernameOrEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email/Username and password are required"));
        }

        var opt = adminUserService.findByUsernameOrEmail(request.getUsernameOrEmail());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }

        var admin = opt.get();
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }

        String role = admin.getRole() != null ? admin.getRole().getName() : null;
        if (role == null || !(role.equalsIgnoreCase("SUPER_ADMIN")
                || role.equalsIgnoreCase("OWNER") || role.equalsIgnoreCase("MANAGER"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Access denied for this role"));
        }

        String token = jwtUtil.generateToken(admin);

        Map<String, Object> adminData = new HashMap<>();
        adminData.put("id", admin.getAdminId());
        adminData.put("username", admin.getUsername());
        adminData.put("firstName", admin.getFirstName());
        adminData.put("lastName", admin.getLastName());
        adminData.put("email", admin.getEmail());
        adminData.put("role", role);

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "token", token,
                "role", role,
                "admin", adminData
        ));
    }

    /* =====================================================================================
     *  OWNER EMAIL-OTP SIGNUP (REAL OTP)
     * ===================================================================================== */

    @Operation(summary = "Owner signup: send email OTP")
    @PostMapping("/owner/send-verification-email")
    public ResponseEntity<?> ownerSendVerificationEmail(
            @RequestParam String email,
            @RequestParam String password
    ) {
        try {
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
            }
            if (adminUserService.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }

            // Send a real OTP to the email (stored hashed with TTL)
            ownerOtpService.generateAndSendOtp(email);

            // Note: we do NOT store password yet; we hash it only after OTP verification
            return ResponseEntity.ok(Map.of(
                    "message", "Verification code sent to email",
                    "email", email
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Owner signup: verify email OTP and issue registration token")
    @PostMapping("/owner/verify-email-code")
    public ResponseEntity<?> ownerVerifyEmailCode(@RequestBody Map<String, String> req) {
        try {
            String email = req.get("email");
            String code  = req.get("code");
            String password = req.get("password");

            if (email == null || code == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "email, code, and password are required"));
            }

            boolean ok = ownerOtpService.verify(email, code);
            if (!ok) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid or expired code"));
            }

            String passwordHash = passwordEncoder.encode(password);

            // 15 minutes registration token with email + hashed password
            String regToken = jwtUtil.generateOwnerRegistrationToken(email, passwordHash, 15 * 60 * 1000);

            return ResponseEntity.ok(Map.of(
                    "message", "Verification successful",
                    "registrationToken", regToken
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Owner signup: complete profile and create account")
    @PostMapping("/owner/complete-profile")
    public ResponseEntity<?> ownerCompleteProfile(@RequestBody Map<String, String> req) {
        try {
            String registrationToken = req.get("registrationToken");
            String username   = req.get("username");
            String firstName  = req.get("firstName");
            String lastName   = req.get("lastName");

            if (registrationToken == null || username == null || firstName == null || lastName == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "registrationToken, username, firstName, lastName are required"));
            }

            Map<String, Object> claims = jwtUtil.parseOwnerRegistrationToken(registrationToken);
            String email = (String) claims.get("email");
            String passwordHash = (String) claims.get("passwordHash");

            if (adminUserService.findByUsername(username).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already in use"));
            }
            if (adminUserService.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }

            var ownerRole = roleRepository.findByName("OWNER")
                    .orElseThrow(() -> new RuntimeException("Role OWNER not found"));

            AdminUser owner = new AdminUser();
            owner.setUsername(username);
            owner.setFirstName(firstName);
            owner.setLastName(lastName);
            owner.setEmail(email);
            owner.setPasswordHash(passwordHash);
            owner.setRole(ownerRole);

            adminUserService.save(owner);

            String token = jwtUtil.generateToken(owner);

            Map<String, Object> ownerData = new HashMap<>();
            ownerData.put("id", owner.getAdminId());
            ownerData.put("username", owner.getUsername());
            ownerData.put("firstName", owner.getFirstName());
            ownerData.put("lastName", owner.getLastName());
            ownerData.put("email", owner.getEmail());
            ownerData.put("role", "OWNER");

            return ResponseEntity.ok(Map.of(
                    "message", "Owner registered successfully",
                    "token", token,
                    "owner", ownerData
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /* ========= Helpers ========= */

    private Long toLongOrNull(Object v) {
        if (v == null) return null;
        try { return Long.valueOf(v.toString()); } catch (Exception e) { return null; }
    }

    private Map<String, Object> baseBusinessMap(Businesses business) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", business.getId());
        m.put("businessName", business.getBusinessName());
        m.put("email", business.getEmail());
        m.put("phoneNumber", business.getPhoneNumber());
        m.put("websiteUrl", business.getWebsiteUrl());
        m.put("description", business.getDescription());
        m.put("businessLogo", business.getBusinessLogoUrl());
        m.put("businessBanner", business.getBusinessBannerUrl());
        return m;
    }
}
