package com.build4all.controller;

import com.build4all.dto.UserDto;
import com.build4all.entities.AdminUsers;
import com.build4all.entities.Users;
import com.build4all.entities.UserStatus; 

import com.build4all.services.AdminUserService;
import com.build4all.services.UserService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.build4all.repositories.*;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UsersController {

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private UsersRepository usersRepository;
	
	@Autowired
	private AdminUserService adminUserService;
	
	@Autowired
	private UserStatusRepository userStatusRepository;


    private final UserService userService;

    public UsersController(UserService userService) {
        this.userService = userService;
    }

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }

            token = token.substring(7).trim();

            // Allow if token is for business
            if (jwtUtil.isBusinessToken(token)) {
                return ResponseEntity.ok(userService.getAllUserDtos());
            }

            // Allow if it's admin with SUPER_ADMIN role
            String role = jwtUtil.extractRole(token);
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.ok(userService.getAllUserDtos());
            }
            

            // Allow if it's a regular user (i.e. no role means it's a user token)
            if (role == null || "USER".equalsIgnoreCase(role)) {
                return ResponseEntity.ok(userService.getAllUserDtos());
            }

            return ResponseEntity.status(403).body("Access denied");

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid or expired token");
        }
    }
    
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody,
            @RequestHeader("Authorization") String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }

            token = token.substring(7).trim();

            // ✅ DEBUG: print extracted email from token
            String contact = jwtUtil.extractUsername(token);
            Users user = usersRepository.findByEmail(contact);

            if (user == null) {
                user = usersRepository.findByPhoneNumber(contact);
            }

            System.out.println("Extracted email from token: " + user);

            String role = jwtUtil.extractRole(token);

            // SUPER_ADMIN case
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
                boolean deleted = userService.deleteUserById(id);
                return deleted
                        ? ResponseEntity.ok("User deleted by SUPER_ADMIN successfully")
                        : ResponseEntity.status(404).body("User not found");
            }

           
            if (user == null || (!user.getId().equals(id) && !"SUPER_ADMIN".equalsIgnoreCase(role))) {
                return ResponseEntity.status(403).body("Access denied: user not found or not authorized");
            }

            String password = requestBody.get("password");
            if (password == null || password.isEmpty()) {
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
    
   

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> sendResetCode(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            Users user = usersRepository.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No user found with this email"));
            }

            boolean success = userService.resetPassword(email);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "Reset code sent to your email"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to send reset code"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");

            if (userService.verifyResetCode(email, code)) {
                return ResponseEntity.ok(Map.of("message", "Code verified successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid code"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/update-password")
    public ResponseEntity<Map<String, String>> updatePassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String newPassword = request.get("newPassword");

            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "New password is required"));
            }

            boolean updated = userService.updatePasswordDirectly(email, newPassword);
            if (updated) {
                return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "User not found"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }
    
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/{userId}/categoriess")
    public ResponseEntity<?> addUserCategories(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds
    ) {
        userService.addUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categories added successfully"));
    }
    
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @DeleteMapping("/delete-profile-image/{id}")
    public ResponseEntity<?> deleteProfileImage(@PathVariable Long id) {
        boolean success = userService.deleteUserProfileImage(id);
        if (success) {
            return ResponseEntity.ok("Profile image deleted successfully");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No profile image found or already deleted");
        }
    }

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/profile-visibility")
    public ResponseEntity<String> updateProfileVisibility(@RequestParam boolean isPublic,
                                                          @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }

            String contact = jwtUtil.extractUsername(token.substring(7).trim());
            Users user = usersRepository.findByEmail(contact);
            if (user == null) {
                user = usersRepository.findByPhoneNumber(contact);
            }

            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            user.setIsPublicProfile(isPublic);
            usersRepository.save(user);

            return ResponseEntity.ok("Profile visibility updated to " + (isPublic ? "PUBLIC" : "PRIVATE"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        Optional<Users> userOpt = usersRepository.findById(id);
        return userOpt.map(user -> ResponseEntity.ok(new UserDto(user)))
                      .orElse(ResponseEntity.status(404).build());
    }
    
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    ) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid token");
        }

        String contact = jwtUtil.extractUsername(token.substring(7).trim());
        Users user = usersRepository.findByEmail(contact);
        if (user == null) {
            user = usersRepository.findByPhoneNumber(contact);
        }

        if (user == null || !user.getId().equals(id)) {
            return ResponseEntity.status(403).body("Access denied");
        }

        String statusStr = requestBody.get("status");
        String password = requestBody.get("password");

        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest().body("Status is required");
        }

        // Only require password if status is set to INACTIVE
        if ("INACTIVE".equalsIgnoreCase(statusStr)) {
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Password is required to deactivate account.");
            }

            boolean isPasswordValid = userService.checkPassword(user, password);
            if (!isPasswordValid) {
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
    }

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    	@GetMapping("/{userId}/suggestions")
    	public ResponseEntity<?> getFriendSuggestions(
    	        @PathVariable Long userId,
    	        @RequestHeader("Authorization") String token) {

    	    try {
    	        // Optional (if you validate the token manually):
    	        // String userIdFromToken = jwtUtil.extractUserId(token);

    	        List<Users> suggestions = userService.suggestFriendsByCategory(userId);
    	        List<UserDto> result = suggestions.stream().map(UserDto::new).toList();
    	        return ResponseEntity.ok(result);
    	    } catch (Exception e) {
    	        return ResponseEntity.status(500).body("Error fetching suggestions: " + e.getMessage());
    	    }
    	}

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/{id}/visibility-status")
    public ResponseEntity<String> updateVisibilityAndStatus(
            @PathVariable Long id,
            @RequestParam boolean isPublicProfile,
            @RequestParam UserStatus status){
        boolean updated = userService.updateVisibilityAndStatus(id, isPublicProfile, status);
        if (updated) {
            return ResponseEntity.ok("Visibility and status updated successfully.");
        } else {
            return ResponseEntity.status(404).body("User not found.");
        }
    }
    
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> requestBody) {

        // 🛡️ Optional: Verify the token matches the user's ID, like isAuthorized(token, id)
        String newStatus = requestBody.get("status");
        String password = requestBody.get("password");

        if (newStatus == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing status"));
        }

        try {
            Users user = userService.getUserById(id); // throws if not found

            // ✅ Require password check if status is INACTIVE
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
    
    @PutMapping("/auth/google/status")
    public ResponseEntity<?> updateGoogleUserStatus(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> requestBody) {
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
    
    @GetMapping("/{userId}/categories")
    public ResponseEntity<?> getUserCategories(@PathVariable Long userId) {
        try {
            List<String> categories = userService.getUserCategories(userId); // returns category names or DTOs
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to fetch categories");
        }
    }

    
    @PutMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> updateUserCategory(
            @PathVariable Long userId,
            @PathVariable Long categoryId,
            @RequestBody Map<String, String> body) {
        try {
            String newCategoryName = body.get("name");
            if (newCategoryName == null || newCategoryName.isBlank()) {
                return ResponseEntity.badRequest().body("Category name is required");
            }

            boolean updated = userService.updateUserCategory(userId, categoryId, newCategoryName);
            if (updated) {
                return ResponseEntity.ok("Category updated successfully");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Category not found for this user");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating category");
        }
    }

    
    @DeleteMapping("/{userId}/categories/{categoryId}")
    public ResponseEntity<?> deleteUserCategory(@PathVariable Long userId, @PathVariable Long categoryId) {
        try {
            boolean deleted = userService.deleteUserCategory(userId, categoryId);
            if (deleted) {
                return ResponseEntity.ok("Category removed successfully");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Category not found for this user");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete category");
        }
    }
    
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/{userId}/UpdateCategory")
    public ResponseEntity<?> replaceUserCategories(
            @PathVariable Long userId,
            @RequestBody List<Long> categoryIds
    ) {
        userService.replaceUserCategories(userId, categoryIds);
        return ResponseEntity.ok(Map.of("message", "User categoriess added successfully"));
    }
    
    
 
    
}