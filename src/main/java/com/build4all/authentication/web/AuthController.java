package com.build4all.authentication.web;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import com.build4all.business.repository.BusinessStatusRepository;
import com.build4all.role.domain.Role;
import com.build4all.authentication.dto.AdminLoginRequest;
import com.build4all.admin.dto.AdminRegisterRequest;
import com.build4all.role.repository.RoleRepository;
import com.build4all.user.domain.UserStatus;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UserStatusRepository;
import com.build4all.user.repository.UsersRepository;
import com.build4all.admin.service.AdminUserService;
import com.build4all.business.repository.BusinessesRepository;
import com.build4all.business.service.BusinessService;
import com.build4all.authentication.service.GoogleAuthService;
import com.build4all.authentication.service.OwnerOtpService;
import com.build4all.user.service.UserService;
import com.build4all.security.JwtUtil;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user login, registration, and Google login")
public class AuthController {

    @Autowired private UserService userService;
    @Autowired private BusinessService businessService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private GoogleAuthService googleAuthService;
    @Autowired private UsersRepository UserRepository;
    @Autowired private UserStatusRepository userStatusRepository;
    @Autowired private BusinessesRepository businessRepository;
    @Autowired private BusinessStatusRepository businessStatusRepository;
    @Autowired private OwnerOtpService ownerOtpService;

    /* ================= USER SIGNUP (OWNER LINK) ================= */

    @PostMapping(value = "/send-verification")
    public ResponseEntity<?> sendVerification(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam("password") String password,
            @RequestParam("ownerProjectLinkId") Long ownerProjectLinkId
    ) {
        try {
            Map<String, String> data = new HashMap<>();
            if (email != null) data.put("email", email);
            if (phoneNumber != null) data.put("phoneNumber", phoneNumber);
            data.put("password", password);

            userService.sendVerificationCodeForRegistration(data, ownerProjectLinkId);
            return ResponseEntity.ok(Map.of("message", "Verification code sent"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<?> verifyEmailCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");

            Long userId = userService.verifyEmailCodeAndRegister(email, code);

            return ResponseEntity.ok(Map.of(
                    "message", "Verification successful. Continue with profile setup.",
                    "user", Map.of("id", userId)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/user/verify-phone-code")
    public ResponseEntity<?> verifyUserPhoneCode(@RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phoneNumber");
            String code = request.get("code");

            Long userId = userService.verifyPhoneCodeAndRegister(phone, code);

            return ResponseEntity.ok(Map.of(
                    "message", "Phone verification successful. Continue with profile setup.",
                    "user", Map.of("id", userId)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
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
            @RequestPart(required = false) MultipartFile profileImage) {

        try {
            if (pendingId == null || username == null || firstName == null || lastName == null || isPublicProfile == null || ownerProjectLinkId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            // per-tenant username check
            if (userService.findByUsername(username, ownerProjectLinkId) != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already in use in this app"));
            }

            boolean updated = userService.completeUserProfile(
                    pendingId, username, firstName, lastName, profileImage, isPublicProfile, ownerProjectLinkId
            );

            if (updated) {
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
                userData.put("ownerProjectLinkId", user.getOwnerProject()!=null? user.getOwnerProject().getId(): null);

                return ResponseEntity.ok(Map.of(
                        "message", "Profile completed successfully.",
                        "user", userData
                ));
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Profile update failed"));

        } catch (BadRequestException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    /* ================ USER LOGIN (OWNER LINK) ================ */

    @PostMapping("/user/login")
    public ResponseEntity<?> userLogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        Long ownerProjectLinkId = toLongOrNull(body.get("ownerProjectLinkId"));

        if (email == null || password == null || ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "email, password and ownerProjectLinkId are required"));
        }

        Users existingUser = userService.findByEmail(email, ownerProjectLinkId);
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not found"));
        }
        if (!passwordEncoder.matches(password, existingUser.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Incorrect password"));
        }

        String status = existingUser.getStatus().getName();
        if ("DELETED".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This account has been deleted and cannot be accessed."));
        }

        if ("INACTIVE".equalsIgnoreCase(status)) {
            String tempToken = jwtUtil.generateToken(existingUser);
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
                "userType", "user"
            ));
        }

        existingUser.setLastLogin(LocalDateTime.now());
        userService.save(existingUser);

        String token = jwtUtil.generateToken(existingUser);

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
            return ResponseEntity.badRequest().body(Map.of("message", "phoneNumber, password and ownerProjectLinkId are required"));
        }

        Users existingUser = userService.findByPhoneNumber(phone, ownerProjectLinkId);
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not found with this phone number"));
        }

        if (!passwordEncoder.matches(rawPassword, existingUser.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Incorrect password"));
        }

        String currentStatus = existingUser.getStatus().getName();
        if ("DELETED".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This account has been deleted and cannot be accessed."));
        }

        if ("INACTIVE".equalsIgnoreCase(currentStatus)) {
            String tempToken = jwtUtil.generateToken(existingUser);

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
                "userType", "user"
            ));
        }

        existingUser.setLastLogin(LocalDateTime.now());
        userService.save(existingUser);

        String token = jwtUtil.generateToken(existingUser);

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

    /* ================== REST OF YOUR CONTROLLER (unchanged) ================== */
    // reactivate, business flows, admin login, owner OTP, etc...
    // (keep your existing implementations)

    @PostMapping("/resend-user-code")
    public ResponseEntity<?> resendUserCode(@RequestBody Map<String, String> request) {
        // your UserService.resendVerificationCode(contact) currently throws UnsupportedOperationException
        return ResponseEntity.badRequest().body(Map.of("message", "Resend user code not supported in owner-linked flow."));
    }

    // ... keep your other endpoints (manager/superadmin login, business, owner OTP) as you posted ...

    private Long toLongOrNull(Object v) {
        if (v == null) return null;
        try { return Long.valueOf(v.toString()); } catch (Exception e) { return null; }
    }
}
