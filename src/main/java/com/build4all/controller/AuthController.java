package com.build4all.controller;

import com.build4all.repositories.*;
import com.build4all.entities.*;


import com.build4all.security.JwtUtil;
import com.build4all.services.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
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
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.build4all.dto.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user login, registration, and Google login")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private UsersRepository UserRepository;
    
    @Autowired
    private UserStatusRepository userStatusRepository;

    @Autowired
    private BusinessesRepository businessRepository;

    @Autowired
    private BusinessStatusRepository businessStatusRepository;

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
            return ResponseEntity.badRequest().body(Map.of("message",  e.getMessage()));
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
                    "user", Map.of("id", userId) // ✅ fix here as well
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /// user register with number
    @PostMapping("/user/verify-phone-code")
    public ResponseEntity<?> verifyUserPhoneCode(@RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phoneNumber");
            String code = request.get("code");

            Long userId = userService.verifyPhoneCodeAndRegister(phone, code);

            // ✅ Wrap in "user" object as expected by frontend
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
            @RequestParam String password)
             {
        try {
            Map<String, String> businessData = new HashMap<>();

            if (email != null) businessData.put("email", email);
            if (phoneNumber != null) businessData.put("phoneNumber", phoneNumber);
            businessData.put("password", password);
       
            businessData.put("status", "ACTIVE"); // Optional: always start as active

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
            return ResponseEntity.badRequest().body(Map.of("message",  e.getMessage()));
        }
    }


    @PostMapping("/business/verify-code")
    public ResponseEntity<?> verifyBusinessCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");

            Long pendingId = businessService.verifyBusinessEmailCode(email, code);

            // ✅ Wrap in a JSON structure with "business.id"
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

            // ✅ Return pendingId in "business.id" format
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
            @RequestPart(required = false) MultipartFile banner
    ) {
        try {
            // 1) Validate input
            if (businessId == null || businessName == null || businessName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "businessId and businessName are required"));
            }

            // 2) Uniqueness check BEFORE saving, excluding the current business
            boolean nameTakenByOther = businessService
                    .existsByBusinessNameIgnoreCaseAndIdNot(businessName.trim(), businessId);
            if (nameTakenByOther) {
                return ResponseEntity.badRequest().body(Map.of("error", "Business name already in use"));
            }

            // 3) Complete the profile
            Businesses updatedBusiness = businessService.completeBusinessProfile(
                    businessId, businessName.trim(), description, websiteUrl, logo, banner
            );

            // 4) Build response
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

        // ✅ Normal login
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

        BusinessStatus activeStatus = businessStatusRepository.findByName("ACTIVE")
            .orElseThrow(() -> new RuntimeException("ACTIVE status not found"));

        business.setStatus(activeStatus);
        businessRepository.save(business);
        
        jwtUtil.generateToken(business);

       // String token = jwtUtil.generateToken(business.getEmail(), "BUSINESS");
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

        // ✅ Return updated user data in response
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

        // ✅ Normal login
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


    @PostMapping("/business/login")
    public ResponseEntity<?> businessLogin(@RequestBody @Valid Users user) {
    	Businesses business = businessService.findByEmail(user.getEmail());

    	if (business == null) {
    	    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
    	        .body(Map.of("message", "Business not found"));
    	}

    

        if (!passwordEncoder.matches(user.getPasswordHash(), business.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Incorrect password"));
        }

        String statusName = business.getStatus() != null ? business.getStatus().getName() : "";
        
        if ("INACTIVEBYADMIN".equalsIgnoreCase(statusName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Your account has been deactivated by the administrator due to low rating. Please contact support for further assistance"));
        }

        if ("DELETED".equalsIgnoreCase(statusName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This account has been deleted and cannot be accessed."));
        }

        if ("INACTIVE".equalsIgnoreCase(statusName)) {
            String tempToken = jwtUtil.generateToken(business);

            Map<String, Object> businessData = new HashMap<>();
            businessData.put("id", business.getId());
            businessData.put("businessName", business.getBusinessName());
            businessData.put("email", business.getEmail());
            businessData.put("phoneNumber", business.getPhoneNumber());
            businessData.put("websiteUrl", business.getWebsiteUrl());
            businessData.put("description", business.getDescription());
            businessData.put("businessLogo", business.getBusinessLogoUrl());
            businessData.put("businessBanner", business.getBusinessBannerUrl());
            businessData.put("userType", "business");

            return ResponseEntity.ok(Map.of(
                    "wasInactive", true,
                    "token", tempToken,
                    "business", businessData
            ));
        }

        business.setLastLoginAt(LocalDateTime.now());
        businessService.save(business);

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
                "business", businessData,
                "wasInactive", false
        ));
    }

    // business login with number
    @PostMapping("/business/login-phone")
    public ResponseEntity<?> businessLoginWithPhone(@RequestBody @Valid Users user) {
        String phone = user.getPhoneNumber();
        String rawPassword = user.getPasswordHash();

        if (phone == null || rawPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number and password are required"));
        }

        Optional<Businesses> optionalBusiness = businessService.findByPhoneNumber(phone);

        if (optionalBusiness.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Business not found with this phone number"));
        }

        Businesses business = optionalBusiness.get();

       
        if (!passwordEncoder.matches(rawPassword, business.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Incorrect password"));
        }

        String statusName = business.getStatus() != null ? business.getStatus().getName() : "";
        
        if ("INACTIVEBYADMIN".equalsIgnoreCase(statusName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Your account has been deactivated by the administrator due to low rating. Please contact support for further assistance"));
        }

        if ("DELETED".equalsIgnoreCase(statusName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This account has been deleted and cannot be accessed."));
        }

        if ("INACTIVE".equalsIgnoreCase(statusName)) {
            String tempToken = jwtUtil.generateToken(business);

            Map<String, Object> businessData = new HashMap<>();
            businessData.put("id", business.getId());
            businessData.put("businessName", business.getBusinessName());
            businessData.put("email", business.getEmail());
            businessData.put("phoneNumber", business.getPhoneNumber());
            businessData.put("websiteUrl", business.getWebsiteUrl());
            businessData.put("description", business.getDescription());
            businessData.put("businessLogo", business.getBusinessLogoUrl());
            businessData.put("businessBanner", business.getBusinessBannerUrl());
            businessData.put("userType", "business");


            return ResponseEntity.ok(Map.of(
                    "wasInactive", true,
                    "token", tempToken,
                    "business", businessData
            ));
        }

        business.setLastLoginAt(LocalDateTime.now());
        businessService.save(business);

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
                "message", "Business login with phone successful",
                "token", token,
                "business", businessData,
                "wasInactive", false
        ));
    }

   @Operation(summary = "Login as a Manager", description = "Authenticates a Manager from the AdminUsers table based on email/username and password", responses = {
        @ApiResponse(responseCode = "200", description = "Login successful, JWT token returned"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or not a manager")
})
@PostMapping("/manager/login")
public ResponseEntity<?> managerLogin(@RequestBody AdminLoginRequest request) {
    // 1. Find manager by username or email
    Optional<AdminUsers> optionalAdmin = adminUserService.findByUsernameOrEmail(request.getUsernameOrEmail());

    // 2. If not found, error
    if (optionalAdmin.isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid credentials"));
    }

    AdminUsers admin = optionalAdmin.get();

    // 3. Password check
    if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid credentials"));
    }

    // 4. Role check (original logic, do not remove)
    if (!"MANAGER".equalsIgnoreCase(admin.getRole().getName())) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Access denied: Not a Manager"));
    }

    // 5. Business status check (added)
    if (admin.getBusiness() != null) {
        Businesses business = admin.getBusiness();
        String status = (business.getStatus() != null) ? business.getStatus().getName().toUpperCase() : "";

        // Block if disabled, inactive, or deleted by any means
        if ("INACTIVE".equals(status) ||
            "INACTIVEBYADMIN".equals(status) ||
            "INACTIVEBYBUSINESS".equals(status) ||
            "DELETED".equals(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Cannot log in: This business account is disabled or deleted. Please contact support."));
        }
    }

    // 6. JWT
    String token = jwtUtil.generateToken(admin);

    // 7. Manager data
    Map<String, Object> managerData = new HashMap<>();
    managerData.put("id", admin.getAdminId());
    managerData.put("username", admin.getUsername());
    managerData.put("firstName", admin.getFirstName());
    managerData.put("lastName", admin.getLastName());
    managerData.put("email", admin.getEmail());
    managerData.put("role", admin.getRole().getName());

    // 8. Business data (the business that promoted this manager)
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
        // Add other fields as needed
    }

    // 9. Return token, manager, and business info
    return ResponseEntity.ok(Map.of(
            "message", "Manager login successful",
            "token", token,
            "manager", managerData,
            "business", businessData
    ));
}


    @Operation(summary = "Login as Super Admin", description = "Authenticates a Super Admin using email and password", responses = {
            @ApiResponse(responseCode = "200", description = "Login successful, JWT returned"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or not a Super Admin")
    })
    @PostMapping("/superadmin/login")
    public ResponseEntity<?> superAdminLogin(@RequestBody AdminLoginRequest request) {
        if (request.getUsernameOrEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Email and password are required"));
        }
        
        
        

        // Force login by email only
        Optional<AdminUsers> adminOpt = adminUserService.findByEmail(request.getUsernameOrEmail());

        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No Super Admin found with this email"));
        }

        AdminUsers admin = adminOpt.get();
        

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
        // Check if email or username already exists
        if (adminUserService.findByUsernameOrEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Username or email already in use"));
        }

        // Fetch the SUPER_ADMIN role
        Role role = roleRepository.findByName("SUPER_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role SUPER_ADMIN not found"));

        // Create the admin user
        AdminUsers newAdmin = new AdminUsers();
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

    @Operation(summary = "Remove a manager", description = "Deletes a manager from AdminUsers and BusinessAdmins tables")
    @ApiResponse(responseCode = "200", description = "Manager removed successfully")
    @ApiResponse(responseCode = "404", description = "Manager not found")

    @DeleteMapping("/admin/remove-manager/{adminId}")
    public ResponseEntity<?> removeManager(@PathVariable Long adminId){
        Optional<AdminUsers> optionalManager = adminUserService.findById(adminId);

        if (optionalManager.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Manager not found"));
        }

        adminUserService.deleteManagerById(adminId);

        return ResponseEntity.ok(Map.of("message", "Manager removed successfully"));}

}