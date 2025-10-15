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
import com.build4all.authentication.service.OwnerOtpService; // <-- NEW
import com.build4all.user.service.UserService;

import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    // NEW: real OTP generator/validator for owner email signup
    @Autowired private OwnerOtpService ownerOtpService;

    @PostMapping(value = "/send-verification")
    public ResponseEntity<?> sendVerification(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam("password") String password) {
        try {
            Map<String, String> data = new HashMap<>();
            if (email != null) data.put("email", email);
            if (phoneNumber != null) data.put("phoneNumber", phoneNumber);
            data.put("password", password);

            userService.sendVerificationCodeForRegistration(data);
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

    // user register with number
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
            @RequestPart(required = false) MultipartFile profileImage) {

        try {
            if (pendingId == null || username == null || firstName == null || lastName == null || isPublicProfile == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            if (userService.existsByUsername(username)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already in use"));
            }

            boolean updated = userService.completeUserProfile(
                    pendingId, username, firstName, lastName, profileImage, isPublicProfile
            );

            if (updated) {
                Users user = userService.findByUsername(username);
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

    @PostMapping("/business/send-verification")
    public ResponseEntity<?> sendBusinessVerification(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam String password) {
        try {
            Map<String, String> businessData = new HashMap<>();
            if (email != null) businessData.put("email", email);
            if (phoneNumber != null) businessData.put("phoneNumber", phoneNumber);
            businessData.put("password", password);
            businessData.put("status", "ACTIVE");

            Long pendingId = businessService.sendBusinessVerificationCode(businessData);

            return ResponseEntity.ok(Map.of(
                    "pendingId", pendingId,
                    "message", phoneNumber != null
                            ? "Static code 123456 set for phone verification"
                            : "Verification code sent to email"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/business/verify-code")
    public ResponseEntity<?> verifyBusinessCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");

            Long pendingId = businessService.verifyBusinessEmailCode(email, code);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> business = new HashMap<>();
            business.put("id", pendingId);
            response.put("business", business);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/business/verify-phone-code")
    public ResponseEntity<?> verifyBusinessPhoneCode(@RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");
            String code = request.get("code");

            Long pendingId = businessService.verifyBusinessPhoneCode(phoneNumber, code);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> business = new HashMap<>();
            business.put("id", pendingId);
            response.put("business", business);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/business/complete-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> completeBusinessProfile(
            @RequestParam Long businessId,
            @RequestParam String businessName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String websiteUrl,
            @RequestPart(required = false) MultipartFile logo,
            @RequestPart(required = false) MultipartFile banner) {
        try {
            if (businessId == null || businessName == null || businessName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "businessId and businessName are required"));
            }

            boolean nameTakenByOther = businessService
                    .existsByBusinessNameIgnoreCaseAndIdNot(businessName.trim(), businessId);
            if (nameTakenByOther) {
                return ResponseEntity.badRequest().body(Map.of("error", "Business name already in use"));
            }

            Businesses updatedBusiness = businessService.completeBusinessProfile(
                    businessId, businessName.trim(), description, websiteUrl, logo, banner
            );

            Map<String, Object> businessData = new HashMap<>();
            businessData.put("id", updatedBusiness.getId());
            businessData.put("businessName", updatedBusiness.getBusinessName());
            businessData.put("email", updatedBusiness.getEmail());
            businessData.put("phoneNumber", updatedBusiness.getPhoneNumber());
            businessData.put("websiteUrl", updatedBusiness.getWebsiteUrl());
            businessData.put("description", updatedBusiness.getDescription());
            businessData.put("businessLogo", updatedBusiness.getBusinessLogoUrl());
            businessData.put("businessBanner", updatedBusiness.getBusinessBannerUrl());
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

    @PostMapping("/resend-user-code")
    public ResponseEntity<?> resendUserCode(@RequestBody Map<String, String> request) {
        String contact = request.get("emailOrPhone");
        try {
            userService.resendVerificationCode(contact);
            return ResponseEntity.ok(Map.of("message", "Verification code resent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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

    // User Login
    @PostMapping("/user/login")
    public ResponseEntity<?> userLogin(@RequestBody @Valid Users user) {
        Users existingUser = userService.findByEmail(user.getEmail());
        if (existingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not found"));
        }
        if (!passwordEncoder.matches(user.getPasswordHash(), existingUser.getPasswordHash())) {
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

            Map<String, Object> response = new HashMap<>();
            response.put("wasInactive", true);
            response.put("message", "Your account is inactive. Confirm reactivation.");
            response.put("token", tempToken);
            response.put("user", inactiveUserData);
            response.put("userType", "user");

            return ResponseEntity.ok(response);
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

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User login successful");
        response.put("token", token);
        response.put("user", userData);
        response.put("wasInactive", false);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reactivate")
    public ResponseEntity<?> reactivateAccount(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("id").toString());

        Optional<Users> userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Users user = userOpt.get();
        String currentStatus = user.getStatus().getName();
        if (!"INACTIVE".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not inactive"));
        }

        UserStatus activeStatus = userStatusRepository.findByNameIgnoreCase("ACTIVE")
                .orElseThrow(() -> new RuntimeException("Status 'ACTIVE' not found"));

        user.setStatus(activeStatus);
        user.setLastLogin(LocalDateTime.now());
        userService.save(user);

        String token = jwtUtil.generateToken(user);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("firstName", user.getFirstName());
        userData.put("lastName", user.getLastName());
        userData.put("email", user.getEmail());
        userData.put("profilePictureUrl", user.getProfilePictureUrl());

        return ResponseEntity.ok(Map.of(
                "message", "Account reactivated successfully",
                "token", token,
                "user", userData
        ));
    }

    @PostMapping("/business/reactivate")
    public ResponseEntity<?> reactivateBusiness(@RequestBody Map<String, Long> request) {
        Long id = request.get("id");

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

        String token = jwtUtil.generateToken(business);

        Map<String, Object> businessData = new HashMap<>();
        businessData.put("id", business.getId());
        businessData.put("businessName", business.getBusinessName());
        businessData.put("email", business.getEmail());
        businessData.put("phoneNumber", business.getPhoneNumber());
        businessData.put("websiteUrl", business.getWebsiteUrl());
        businessData.put("description", business.getDescription());
        businessData.put("businessLogo", business.getBusinessLogoUrl());
        businessData.put("businessBanner", business.getBusinessBannerUrl());

        return ResponseEntity.ok(Map.of(
                "message", "Business login successful",
                "token", token,
                "business", businessData
        ));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestPart("firstName") String firstName,
            @RequestPart("lastName") String lastName,
            @RequestPart("username") String username,
            @RequestPart(value = "password", required = false) String password,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture
    ) throws IOException {

        Optional<Users> optionalUser = UserRepository.findById(id);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        Users user = optionalUser.get();

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);

        if (profilePicture != null && !profilePicture.isEmpty()) {
            String imageUrl = userService.saveProfileImage(profilePicture);
            user.setProfilePictureUrl(imageUrl);
        }

        if (password != null && !password.isEmpty()) {
            String hashedPassword = passwordEncoder.encode(password);
            user.setPasswordHash(hashedPassword);
        }

        UserRepository.save(user);

        Map<String, Object> updatedData = new HashMap<>();
        updatedData.put("id", user.getId());
        updatedData.put("firstName", user.getFirstName());
        updatedData.put("lastName", user.getLastName());
        updatedData.put("username", user.getUsername());
        updatedData.put("email", user.getEmail());
        updatedData.put("phoneNumber", user.getPhoneNumber());
        updatedData.put("profilePictureUrl", user.getProfilePictureUrl());

        return ResponseEntity.ok(updatedData);
    }

    // user login with number
    @PostMapping("/user/login-phone")
    public ResponseEntity<?> userLoginWithPhone(@RequestBody @Valid Users user) {
        String phone = user.getPhoneNumber();
        String rawPassword = user.getPasswordHash();

        if (phone == null || rawPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number and password are required"));
        }

        Users existingUser = userService.findByPhoneNumber(phone);
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

            Map<String, Object> response = new HashMap<>();
            response.put("wasInactive", true);
            response.put("message", "Your account is inactive. Confirm reactivation.");
            response.put("token", tempToken);
            response.put("user", inactiveUserData);
            response.put("userType", "user");

            return ResponseEntity.ok(response);
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

        Map<String, Object> response = new HashMap<>();
        response.put("message", "User login with phone successful");
        response.put("token", token);
        response.put("user", userData);
        response.put("wasInactive", false);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Login as a Manager", description = "Authenticates a Manager from the AdminUsers table based on email/username and password",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful, JWT token returned"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or not a manager")
            })
    @PostMapping("/manager/login")
    public ResponseEntity<?> managerLogin(@RequestBody AdminLoginRequest request) {
        Optional<AdminUser> optionalAdmin = adminUserService.findByUsernameOrEmail(request.getUsernameOrEmail());
        if (optionalAdmin.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        AdminUser admin = optionalAdmin.get();
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        if (!"MANAGER".equalsIgnoreCase(admin.getRole().getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access denied: Not a Manager"));
        }

        if (admin.getBusiness() != null) {
            Businesses business = admin.getBusiness();
            String status = (business.getStatus() != null) ? business.getStatus().getName().toUpperCase() : "";
            if ("INACTIVE".equals(status) ||
                    "INACTIVEBYADMIN".equals(status) ||
                    "INACTIVEBYBUSINESS".equals(status) ||
                    "DELETED".equals(status)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Cannot log in: This business account is disabled or deleted. Please contact support."));
            }
        }

        String token = jwtUtil.generateToken(admin);

        Map<String, Object> managerData = new HashMap<>();
        managerData.put("id", admin.getAdminId());
        managerData.put("username", admin.getUsername());
        managerData.put("firstName", admin.getFirstName());
        managerData.put("lastName", admin.getLastName());
        managerData.put("email", admin.getEmail());
        managerData.put("role", admin.getRole().getName());

        Map<String, Object> businessData = null;
        if (admin.getBusiness() != null) {
            Businesses business = admin.getBusiness();
            businessData = new HashMap<>();
            businessData.put("id", business.getId());
            businessData.put("businessName", business.getBusinessName());
            businessData.put("email", business.getEmail());
            businessData.put("phoneNumber", business.getPhoneNumber());
            businessData.put("businessLogoUrl", business.getBusinessLogoUrl());
            businessData.put("businessBannerUrl", business.getBusinessBannerUrl());
            businessData.put("description", business.getDescription());
            businessData.put("websiteUrl", business.getWebsiteUrl());
        }

        return ResponseEntity.ok(Map.of(
                "message", "Manager login successful",
                "token", token,
                "manager", managerData,
                "business", businessData
        ));
    }

    @Operation(summary = "Login as Super Admin", description = "Authenticates a Super Admin using email and password",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful, JWT returned"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or not a Super Admin")
            })
    @PostMapping("/superadmin/login")
    public ResponseEntity<?> superAdminLogin(@RequestBody AdminLoginRequest request) {
        if (request.getUsernameOrEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Email and password are required"));
        }

        Optional<AdminUser> adminOpt = adminUserService.findByEmail(request.getUsernameOrEmail());
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No Super Admin found with this email"));
        }

        AdminUser admin = adminOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Incorrect password"));
        }

        if (!"SUPER_ADMIN".equalsIgnoreCase(admin.getRole().getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access denied: Not a Super Admin"));
        }

        String token = jwtUtil.generateToken(admin);

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
                "admin", adminData));
    }

    @Operation(summary = "Register a new Super Admin", description = "Registers a new AdminUser with the default role of SUPER_ADMIN")
    @PostMapping("/admin/register")
    public ResponseEntity<?> registerSuperAdmin(@RequestBody AdminRegisterRequest request) {
        if (adminUserService.findByUsernameOrEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Username or email already in use"));
        }

        Role role = roleRepository.findTopByNameIgnoreCase("SUPER_ADMIN")
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
                "adminId", newAdmin.getAdminId()));
    }

    @Operation(summary = "Remove a manager", description = "Deletes a manager from AdminUsers and AdminUserBusiness tables")
    @ApiResponse(responseCode = "200", description = "Manager removed successfully")
    @ApiResponse(responseCode = "404", description = "Manager not found")
    @DeleteMapping("/admin/remove-manager/{adminId}")
    public ResponseEntity<?> removeManager(@PathVariable Long adminId) {
        Optional<AdminUser> optionalManager = adminUserService.findById(adminId);
        if (optionalManager.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Manager not found"));
        }
        adminUserService.deleteManagerById(adminId);
        return ResponseEntity.ok(Map.of("message", "Manager removed successfully"));
    }

    @Operation(summary = "Register Owner (AdminUser with role=OWNER)")
    @PostMapping("/owner/register")
    public ResponseEntity<?> registerOwner(@RequestBody AdminRegisterRequest request) {
        if (adminUserService.findByUsernameOrEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Username or email already in use"));
        }

        Role ownerRole = roleRepository.findByName("OWNER")
                .orElseThrow(() -> new RuntimeException("Role OWNER not found"));

        AdminUser owner = new AdminUser();
        owner.setUsername(request.getUsername());
        owner.setFirstName(request.getFirstName());
        owner.setLastName(request.getLastName());
        owner.setEmail(request.getEmail());
        owner.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        owner.setRole(ownerRole);

        adminUserService.save(owner);

        return ResponseEntity.ok(Map.of(
                "message", "Owner registered successfully",
                "adminId", owner.getAdminId(),
                "role", "OWNER"
        ));
    }

    // ---------- UNIFIED ADMIN LOGIN (SUPER_ADMIN / OWNER / MANAGER) ----------
    @Operation(summary = "Unified Admin Login (SUPER_ADMIN / OWNER / MANAGER)")
    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody AdminLoginRequest request) {
        if (request.getUsernameOrEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Email/Username and password are required"));
        }

        var opt = adminUserService.findByUsernameOrEmail(request.getUsernameOrEmail());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        var admin = opt.get();
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        String role = admin.getRole() != null ? admin.getRole().getName() : null;
        if (role == null || !(role.equalsIgnoreCase("SUPER_ADMIN")
                || role.equalsIgnoreCase("OWNER") || role.equalsIgnoreCase("MANAGER"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access denied for this role"));
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

    // ---------- OWNER EMAIL OTP SIGNUP (REAL OTP) ----------
    @Operation(summary = "Owner signup: send email OTP")
    @PostMapping("/owner/send-verification-email")
    public ResponseEntity<?> ownerSendVerificationEmail(
            @RequestParam String email,
            @RequestParam String password
    ) {
        try {
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
            }
            if (adminUserService.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email already in use"));
            }

            // Send a real OTP to the email (stored hashed with TTL)
            ownerOtpService.generateAndSendOtp(email);

            // NOTE: don't store password yet; we will hash it only after OTP verification
            return ResponseEntity.ok(Map.of(
                    "message", "Verification code sent to email",
                    "email", email
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
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
                return ResponseEntity.badRequest().body(Map.of("message", "email, code, and password are required"));
            }

            boolean ok = ownerOtpService.verify(email, code);
            if (!ok) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid or expired code"));
            }

            String passwordHash = passwordEncoder.encode(password);

            // 15 minutes registration token with email + hashed password
            String regToken = jwtUtil.generateOwnerRegistrationToken(email, passwordHash, 15 * 60 * 1000);

            return ResponseEntity.ok(Map.of(
                    "message", "Verification successful",
                    "registrationToken", regToken
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
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
                return ResponseEntity.badRequest().body(Map.of("message", "registrationToken, username, firstName, lastName are required"));
            }

            Map<String, Object> claims = jwtUtil.parseOwnerRegistrationToken(registrationToken);
            String email = (String) claims.get("email");
            String passwordHash = (String) claims.get("passwordHash");

            if (adminUserService.findByUsername(username).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username already in use"));
            }
            if (adminUserService.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email already in use"));
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
}
