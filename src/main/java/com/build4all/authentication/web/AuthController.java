// src/main/java/com/build4all/authentication/web/AuthController.java
package com.build4all.authentication.web;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.authentication.dto.AdminLoginRequest;
import com.build4all.admin.dto.AdminRegisterRequest;
import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.regex.Pattern;


import com.build4all.admin.service.AdminUserService;
import com.build4all.authentication.service.GoogleAuthService;
import com.build4all.authentication.service.OwnerOtpService;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import com.build4all.business.repository.BusinessStatusRepository;
import com.build4all.business.repository.BusinessesRepository;
import com.build4all.business.service.BusinessService;
import com.build4all.licensing.service.LicensingService;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;

import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;

import com.build4all.security.JwtUtil;

import com.build4all.user.domain.UserStatus;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UserStatusRepository;
import com.build4all.user.repository.UsersRepository;
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
    @Autowired private UsersRepository userRepository;


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
    @Autowired private LicensingService licensingService;


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
        if (ownerProjectLinkId == null) {
            throw new IllegalArgumentException("ownerProjectLinkId is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }

        boolean emailProvided = email != null && !email.isBlank();
        boolean phoneProvided = phoneNumber != null && !phoneNumber.isBlank();

        if (!emailProvided && !phoneProvided) {
            throw new IllegalArgumentException("Provide email or phoneNumber");
        }
        if (emailProvided && phoneProvided) {
            throw new IllegalArgumentException("Provide only one: email OR phoneNumber");
        }

        Map<String, String> data = new HashMap<>();
        data.put("password", password.trim());

        if (emailProvided) {
            validateEmailBeforeSendingOrThrow(email);
            data.put("email", email.trim());
        } else {
            data.put("phoneNumber", phoneNumber.trim());
        }

        userService.sendVerificationCodeForRegistration(data, ownerProjectLinkId);

        return ResponseEntity.ok(Map.of("message", "Verification code sent"));
    }


    @PostMapping(
            value = "/verify-email-code",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> verifyEmailCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code  = request.get("code");

            if (email == null || code == null || email.isBlank() || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "email and code are required"));
            }

            Long userId = userService.verifyEmailCodeAndRegister(email.trim(), code.trim());

            return ResponseEntity.ok(Map.of(
                    "message", "Verification successful. Continue with profile setup.",
                    "user", Map.of("id", userId)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping(
    	    value = "/send-verification",
    	    consumes = MediaType.APPLICATION_JSON_VALUE,
    	    produces = MediaType.APPLICATION_JSON_VALUE
    	)
    	public ResponseEntity<?> sendVerificationJson(@RequestBody Map<String, Object> body) {
    	    try {
    	        String email = body.get("email") != null ? body.get("email").toString() : null;
    	        String phoneNumber = body.get("phoneNumber") != null ? body.get("phoneNumber").toString() : null;
    	        String password = body.get("password") != null ? body.get("password").toString() : null;

    	        Long ownerProjectLinkId = body.get("ownerProjectLinkId") != null
    	                ? Long.valueOf(body.get("ownerProjectLinkId").toString())
    	                : null;

    	        // reuse existing logic
    	        return sendVerification(email, phoneNumber, password, ownerProjectLinkId);

    	    } catch (Exception e) {
    	        e.printStackTrace();
    	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    	                .body(Map.of("error", "Server error: " + e.getMessage()));
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

            licensingService.requireUserSlotAvailable(ownerProjectLinkId);
            
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
        } catch (RuntimeException e) {
            // if licensing throws
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
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

        licensingService.requireUserSlotAvailable(ownerProjectLinkId);
        
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

        // ✅ optional phone
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            newAdmin.setPhoneNumber(request.getPhoneNumber().trim());
        }

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

    @Operation(summary = "Owner signup: complete profile and create OWNER account (no tenant yet)")
    @PostMapping("/owner/complete-profile")
    public ResponseEntity<?> ownerCompleteProfile(@RequestBody Map<String, Object> req) {
        try {
            String registrationToken = asString(req.get("registrationToken"));
            String username   = asString(req.get("username"));
            String firstName  = asString(req.get("firstName"));
            String lastName   = asString(req.get("lastName"));
            String phoneNumber = asString(req.get("phoneNumber")); // optional

            if (isBlank(registrationToken) || isBlank(username) || isBlank(firstName) || isBlank(lastName)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "registrationToken, username, firstName, and lastName are required"
                ));
            }

            // ✅ Read email + passwordHash from reg token
            Map<String, Object> claims = jwtUtil.parseOwnerRegistrationToken(registrationToken);
            String email = (String) claims.get("email");
            String passwordHash = (String) claims.get("passwordHash");

            if (isBlank(email) || isBlank(passwordHash)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid registration token"));
            }

            username = username.trim();
            firstName = firstName.trim();
            lastName = lastName.trim();
            email = email.trim();
            phoneNumber = isBlank(phoneNumber) ? null : phoneNumber.trim();

            if (adminUserService.findByUsername(username).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already in use"));
            }
            if (adminUserService.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }

            Role ownerRole = roleRepository.findByName("OWNER")
                    .orElseThrow(() -> new RuntimeException("Role OWNER not found"));

            AdminUser owner = new AdminUser();
            owner.setUsername(username);
            owner.setFirstName(firstName);
            owner.setLastName(lastName);
            owner.setEmail(email);
            owner.setPasswordHash(passwordHash);
            owner.setRole(ownerRole);
            owner.setPhoneNumber(phoneNumber);

            adminUserService.save(owner);

            // ✅ IMPORTANT: DO NOT mint JWT here (no project yet)
            return ResponseEntity.ok(Map.of(
                    "message", "Owner registered successfully. Please login.",
                    "ownerId", owner.getAdminId()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }



 /* ===================== Helpers (put inside AuthController) ===================== */

 private String asString(Object v) {
     return v == null ? null : v.toString();
 }

 private boolean isBlank(String s) {
     return s == null || s.trim().isEmpty();
 }


    @PostMapping("/demo/create-apple-review-account")
    public ResponseEntity<?> createAppleReviewDemoAccount(
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestParam Long ownerProjectLinkId
    ) {
        try {

            // ✅ PUBLIC: removed CI protection
            // ResponseEntity<?> gate = requireCiTokenUnlessLocal(authToken);
            // if (gate != null) return gate;

            if (ownerProjectLinkId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "ownerProjectLinkId is required"));
            }

            String demoEmail = Optional.ofNullable(System.getenv("APPLE_REVIEW_DEMO_EMAIL"))
                    .orElse("demo@applereview.com");

            String demoPassword = Optional.ofNullable(System.getenv("APPLE_REVIEW_DEMO_PASSWORD"))
                    .orElse("AppleReview2026!");

            String demoUsernameBase = Optional.ofNullable(System.getenv("APPLE_REVIEW_DEMO_USERNAME"))
                    .orElse("apple_reviewer");

            Users existingUser = userService.findByEmail(demoEmail, ownerProjectLinkId);
            if (existingUser != null) {

                String statusName = existingUser.getStatus() != null ? existingUser.getStatus().getName() : "";
                if (!"ACTIVE".equalsIgnoreCase(statusName)) {
                    UserStatus activeStatus = userStatusRepository.findByNameIgnoreCase("ACTIVE")
                            .orElseThrow(() -> new RuntimeException("Status ACTIVE not found"));
                    existingUser.setStatus(activeStatus);
                    existingUser.setUpdatedAt(LocalDateTime.now());
                    userService.save(existingUser);
                }

                return ResponseEntity.ok(Map.of(
                        "message", "Demo account already exists and is active",
                        "email", demoEmail,
                        "userId", existingUser.getId(),
                        "ownerProjectLinkId", ownerProjectLinkId
                ));
            }

            AdminUserProject link = adminUserProjectRepo.findById(ownerProjectLinkId)
                    .orElseThrow(() -> new RuntimeException("AdminUserProject not found: " + ownerProjectLinkId));

            UserStatus activeStatus = userStatusRepository.findByNameIgnoreCase("ACTIVE")
                    .orElseThrow(() -> new RuntimeException("Status ACTIVE not found"));

            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new RuntimeException("Role USER not found"));

            int suffix = 0;
            String finalUsername = demoUsernameBase;

            while (userRepository.existsByUsernameIgnoreCaseAndOwnerProject_Id(finalUsername, ownerProjectLinkId)) {
                suffix++;
                finalUsername = demoUsernameBase + suffix;
            }

            Users demoUser = new Users();
            demoUser.setOwnerProject(link);
            demoUser.setUsername(finalUsername);
            demoUser.setFirstName("Apple");
            demoUser.setLastName("Reviewer");
            demoUser.setEmail(demoEmail);
            demoUser.setPasswordHash(passwordEncoder.encode(demoPassword));
            demoUser.setStatus(activeStatus);
            demoUser.setRole(userRole);
            demoUser.setIsPublicProfile(true);
            demoUser.setCreatedAt(LocalDateTime.now());
            demoUser.setUpdatedAt(LocalDateTime.now());

            userService.save(demoUser);

            return ResponseEntity.ok(Map.of(
                    "message", "Demo account created successfully",
                    "email", demoEmail,
                    "password", demoPassword,
                    "username", finalUsername,
                    "userId", demoUser.getId(),
                    "ownerProjectLinkId", ownerProjectLinkId
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create demo account: " + e.getMessage()));
        }
    }


    @PostMapping("/demo/seed-apple-review-account-all")
    public ResponseEntity<?> seedAppleReviewDemoAccountForAllApps(
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken
    ) {
        try {

            // ✅ PROTECT endpoint (CI only) unless local
         
            String demoEmail = Optional.ofNullable(System.getenv("APPLE_REVIEW_DEMO_EMAIL"))
                    .orElse("demo@applereview.com");
            String demoPassword = Optional.ofNullable(System.getenv("APPLE_REVIEW_DEMO_PASSWORD"))
                    .orElse("AppleReview2026!");
            String demoUsernameBase = Optional.ofNullable(System.getenv("APPLE_REVIEW_DEMO_USERNAME"))
                    .orElse("apple_reviewer");

            UserStatus activeStatus = userStatusRepository.findByNameIgnoreCase("ACTIVE")
                    .orElseThrow(() -> new RuntimeException("Status ACTIVE not found"));

            // ✅ THE FIX: role_id cannot be null
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new RuntimeException("Role USER not found"));

            List<AdminUserProject> apps = adminUserProjectRepo.findAll();
            if (apps.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No apps found in AdminUserProject"));
            }

            int created = 0;
            int updated = 0;

            for (AdminUserProject link : apps) {
                Long ownerProjectLinkId = link.getId();

                Users existingUser = userService.findByEmail(demoEmail, ownerProjectLinkId);
                if (existingUser != null) {
                    String statusName = existingUser.getStatus() != null ? existingUser.getStatus().getName() : "";
                    if (!"ACTIVE".equalsIgnoreCase(statusName)) {
                        existingUser.setStatus(activeStatus);
                        existingUser.setUpdatedAt(LocalDateTime.now());
                        userService.save(existingUser);
                    }
                    updated++;
                    continue;
                }

                int suffix = 0;
                String finalUsername = demoUsernameBase;
                while (userRepository.existsByUsernameIgnoreCaseAndOwnerProject_Id(finalUsername, ownerProjectLinkId)) {
                    suffix++;
                    finalUsername = demoUsernameBase + suffix;
                }

                Users demoUser = new Users();
                demoUser.setOwnerProject(link);
                demoUser.setUsername(finalUsername);
                demoUser.setFirstName("Apple");
                demoUser.setLastName("Reviewer");
                demoUser.setEmail(demoEmail);
                demoUser.setPasswordHash(passwordEncoder.encode(demoPassword));
                demoUser.setStatus(activeStatus);

                // ✅ MUST SET ROLE
                demoUser.setRole(userRole);

                demoUser.setIsPublicProfile(true);
                demoUser.setCreatedAt(LocalDateTime.now());
                demoUser.setUpdatedAt(LocalDateTime.now());

                userService.save(demoUser);
                created++;
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Seed completed",
                    "email", demoEmail,
                    "appsTotal", apps.size(),
                    "created", created,
                    "updated", updated
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed: " + e.getMessage()));
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
    
    private boolean isLocalProfile() {
        // Works if you run with: -Dspring.profiles.active=local
        String active = System.getProperty("spring.profiles.active", "");
        if (active != null && active.toLowerCase().contains("local")) return true;

        // Works if you run with env var: SPRING_PROFILES_ACTIVE=local
        String envActive = System.getenv("SPRING_PROFILES_ACTIVE");
        return envActive != null && envActive.toLowerCase().contains("local");
    }

    private ResponseEntity<?> requireCiTokenUnlessLocal(String authToken) {
        if (isLocalProfile()) return null; // ✅ local bypass

        String expectedToken = System.getenv("CI_RUNTIME_TOKEN");
        if (expectedToken == null || expectedToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: CI_RUNTIME_TOKEN is not set"));
        }
        if (authToken == null || authToken.isBlank() || !expectedToken.equals(authToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid CI token"));
        }
        return null;
    }
    
 // ===================== Email validation (format + typo + DNS) =====================

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, String> EMAIL_DOMAIN_TYPOS = Map.ofEntries(
            Map.entry("gamil.com", "gmail.com"),
            Map.entry("gmil.com", "gmail.com"),
            Map.entry("gmail.con", "gmail.com"),
            Map.entry("gmail.co", "gmail.com"),
            Map.entry("hotnail.com", "hotmail.com"),
            Map.entry("hotmai.com", "hotmail.com"),
            Map.entry("outlok.com", "outlook.com"),
            Map.entry("outllok.com", "outlook.com"),
            Map.entry("yaho.com", "yahoo.com"),
            Map.entry("yahho.com", "yahoo.com")
    );

    private void validateEmailBeforeSendingOrThrow(String email) {
        if (email == null || email.isBlank()) return;

        String e = email.trim().toLowerCase(Locale.ROOT);

        // 1) strict format
        if (!EMAIL_PATTERN.matcher(e).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }

        String domain = e.substring(e.lastIndexOf('@') + 1);

        // 2) typo-domain block
        if (EMAIL_DOMAIN_TYPOS.containsKey(domain)) {
            throw new IllegalArgumentException(
                    "Invalid email domain. Did you mean " + EMAIL_DOMAIN_TYPOS.get(domain) + " ?"
            );
        }

        // 3) DNS check (must have MX OR A/AAAA)
     
        Boolean dnsOk = hasMxOrARecordSafe(domain);
        if (dnsOk != null && !dnsOk) {
            throw new IllegalArgumentException("Email domain does not exist: " + domain);
        }


    }
    
    
    @PostMapping("/user/resend-code")
    public ResponseEntity<?> resendUserCode(@RequestBody Map<String, String> req) {
        try {
            String email = req.get("email");
            String phone = req.get("phoneNumber");
            Long ownerProjectLinkId = toLongOrNull(req.get("ownerProjectLinkId"));

            if (ownerProjectLinkId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "ownerProjectLinkId is required"));
            }

            boolean emailProvided = email != null && !email.isBlank();
            boolean phoneProvided = phone != null && !phone.isBlank();

            if (!emailProvided && !phoneProvided) {
                return ResponseEntity.badRequest().body(Map.of("error", "Provide email or phoneNumber"));
            }
            if (emailProvided && phoneProvided) {
                return ResponseEntity.badRequest().body(Map.of("error", "Provide only one: email OR phoneNumber"));
            }

            if (emailProvided) validateEmailBeforeSendingOrThrow(email);

            userService.resendVerificationCodeForRegistration(email, phone, ownerProjectLinkId);

            return ResponseEntity.ok(Map.of("message", "Verification code resent"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }


    private Boolean hasMxOrARecordSafe(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");

            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX", "A", "AAAA"});

            Attribute mx = attrs.get("MX");
            Attribute a = attrs.get("A");
            Attribute aaaa = attrs.get("AAAA");

            return (mx != null && mx.size() > 0)
                    || (a != null && a.size() > 0)
                    || (aaaa != null && aaaa.size() > 0);

        } catch (Exception ex) {
            return null; // ✅ don't block if DNS lookup isn't supported
        }
    }



}
