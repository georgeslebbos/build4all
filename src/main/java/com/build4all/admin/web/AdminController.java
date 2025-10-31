package com.build4all.admin.web;

import com.build4all.authentication.dto.AdminPasswordUpdateDTO;
import com.build4all.admin.dto.AdminProfileUpdateDTO;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.business.repository.BusinessStatusRepository;
import com.build4all.catalog.dto.AdminItemDTO;
import com.build4all.review.repository.ReviewRepository;
import com.build4all.user.repository.UserStatusRepository;
import com.build4all.user.repository.UsersRepository;
import com.build4all.business.service.BusinessService;
import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import com.build4all.user.domain.Users;
import com.build4all.notifications.dto.AdminNotificationPreferencesDTO;
import com.build4all.catalog.service.AdminItemService;
import com.build4all.admin.service.AdminStatsService;
import com.build4all.admin.service.AdminUserService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/superadmin")
@Tag(name = "Admin Dashboard", description = "Admin-level statistics and monitoring")
public class AdminController {

    @Autowired private AdminStatsService statsService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private AdminItemService adminItemService;
    @Autowired private UsersRepository usersRepository;
    @Autowired private AdminUsersRepository adminUsersRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private BusinessStatusRepository businessStatusRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired
    private BusinessService businessService;
    
    @Autowired
    private UserStatusRepository userStatusRepository;


    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private boolean isSuperAdmin(String token) {
        try {
            token = token.substring(7).trim();
            String role = jwtUtil.extractRole(token);
            return "SUPER_ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Get system stats", description = "Returns total users, activities, bookings, and feedback for the selected period")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    public ResponseEntity<?> getStats(@RequestParam(defaultValue = "today") String period,
                                      @RequestHeader("Authorization") String token) {
        try {
            token = token.substring(7).trim(); // Remove "Bearer "
            String role = jwtUtil.extractRole(token);

            if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            return ResponseEntity.ok(statsService.getStats(period));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid token");
        }
    }
 
    @GetMapping("/registrations/monthly")
    @Operation(summary = "Get monthly user registration counts", description = "Returns user registration counts per month for the last 6 months")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    public ResponseEntity<?> getMonthlyUserRegistrations(@RequestHeader("Authorization") String token) {
        try {
            token = token.substring(7).trim(); // Remove "Bearer " prefix
            String role = jwtUtil.extractRole(token);

            if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            Map<String, Long> registrations = statsService.getMonthlyRegistrations();
            return ResponseEntity.ok(registrations);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid or expired token");
        }
    }

    @GetMapping("/activities/popular")
    @Operation(summary = "Get popular activities", description = "Returns most booked or viewed activities and their popularity metrics")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    public ResponseEntity<?> getPopularActivities(@RequestHeader("Authorization") String token) {
        try {
            token = token.substring(7).trim(); // Remove "Bearer " prefix
            String role = jwtUtil.extractRole(token);

            if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            List<Map<String, Object>> activities = statsService.getPopularItems();
            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid or expired token");
        }
    }


    @GetMapping("/activities")
    @Operation(summary = "Get all activities posted by businesses", description = "Returns title, business name, date, participants, and description for all activities")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    public ResponseEntity<?> getAllActivities(@RequestHeader("Authorization") String token) {
        try {
            token = token.substring(7).trim(); // Remove "Bearer " prefix
            String role = jwtUtil.extractRole(token);

            if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            List<AdminItemDTO> activities = adminItemService.getAllItems();
            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid or expired token");
        }
    }


    @Operation(summary = "Toggle user status", description = "Toggle a user’s status between ACTIVE and INACTIVE")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/{userId}/toggle-status")
    public ResponseEntity<String> toggleUserStatus(@PathVariable Long userId,
                                                   @RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Optional<Users> optionalUser = usersRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        Users user = optionalUser.get();
        String currentStatus = user.getStatus().getName();

        try {
            if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
                user.setStatus(userStatusRepository.findByName("INACTIVE")
                    .orElseThrow(() -> new RuntimeException("INACTIVE status not found")));
            } else {
                user.setStatus(userStatusRepository.findByName("ACTIVE")
                    .orElseThrow(() -> new RuntimeException("ACTIVE status not found")));
            }

            usersRepository.save(user);
            return ResponseEntity.ok("User status updated to: " + user.getStatus().getName());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to toggle user status: " + e.getMessage());
        }
    }


    @Operation(summary = "Delete user", description = "Permanently delete a user account by ID")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId,
                                             @RequestHeader("Authorization") String token) {
        try {
            token = token.substring(7).trim(); // remove "Bearer " prefix
            String role = jwtUtil.extractRole(token);

            if (!"SUPER_ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            Optional<Users> optionalUser = usersRepository.findById(userId);
            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            adminUserService.deleteUserAndDependencies(userId); // ✅ cascades other related deletions
            return ResponseEntity.ok("User and all related data deleted successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete user: " + e.getMessage());
        }
    }

    @Operation(summary = "Update admin profile", description = "Update admin profile information (first name, last name, username, email)")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/profile")
    public ResponseEntity<?> updateAdminProfile(@RequestBody AdminProfileUpdateDTO dto,
                                                @RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");

        token = token.substring(7).trim(); // Remove "Bearer "
        Long currentAdminId = jwtUtil.extractId(token);
        AdminUser admin = adminUsersRepository.findById(currentAdminId).orElse(null);

        if (admin == null) {
            return ResponseEntity.status(404).body("Admin user not found.");
        }

        admin.setFirstName(dto.getFirstName());
        admin.setLastName(dto.getLastName());
        admin.setUsername(dto.getUsername());
        admin.setEmail(dto.getEmail());

        adminUsersRepository.save(admin);
        
        return ResponseEntity.ok("Admin profile updated successfully.");
    }

    @Operation(summary = "Update admin password", description = "Change the password of a SUPER_ADMIN after verifying the current password")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/password")
    public ResponseEntity<String> updateAdminPassword(@RequestBody AdminPasswordUpdateDTO dto,
                                                      @RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");

        token = token.substring(7).trim();
        Long currentAdminId = jwtUtil.extractId(token);

        Optional<AdminUser> optionalAdmin = adminUsersRepository.findById(currentAdminId);
        if (optionalAdmin.isEmpty()) {
            return ResponseEntity.status(404).body("Admin user not found.");
        }

        AdminUser admin = optionalAdmin.get();
        if (!passwordEncoder.matches(dto.getCurrentPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(403).body("Current password is incorrect.");
        }

        admin.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        adminUsersRepository.save(admin);

        return ResponseEntity.ok("Password updated successfully.");
    }

    @Operation(summary = "Update notification preferences", description = "Update admin notification settings for item and feedback alerts")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/notifications")
    public ResponseEntity<String> updateNotificationPreferences(@RequestBody AdminNotificationPreferencesDTO dto,
                                                                @RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");

        token = token.substring(7).trim();
        Long currentAdminId = jwtUtil.extractId(token);

        AdminUser admin = adminUsersRepository.findById(currentAdminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(404).body("Admin user not found.");
        }

        admin.setNotifyItemUpdates(dto.isNotifyItemUpdates());
        admin.setNotifyUserFeedback(dto.isNotifyUserFeedback());
        adminUsersRepository.save(admin);

        return ResponseEntity.ok("Notification preferences updated successfully.");
    }


    @Operation(summary = "Get all feedback", description = "Returns submitter name, content, rating, and date for all feedback")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @GetMapping("/feedback")
    public ResponseEntity<?> getAllFeedback(@RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");
        return ResponseEntity.ok(reviewRepository.findAll());
    }
    
    @GetMapping("/me")
    @Operation(summary = "Get current admin profile", description = "Returns profile details of the currently logged-in SUPER_ADMIN")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    public ResponseEntity<?> getCurrentAdminProfile(@RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        token = token.substring(7).trim(); // Remove "Bearer "
        Long currentAdminId = jwtUtil.extractId(token);
        Optional<AdminUser> optionalAdmin = adminUsersRepository.findById(currentAdminId);

        if (optionalAdmin.isEmpty()) {
            return ResponseEntity.status(404).body("Admin not found.");
        }

        AdminUser admin = optionalAdmin.get();

        return ResponseEntity.ok(Map.of(
                "id", admin.getAdminId(),
                "firstName", admin.getFirstName(),
                "lastName", admin.getLastName(),
                "username", admin.getUsername(),
                "email", admin.getEmail(),
                "notifyItemUpdates", admin.getNotifyItemUpdates(),
                "notifyUserFeedback", admin.getNotifyUserFeedback()
        ));
    }
    
    @Operation(summary = "Delete a business and all related data", description = "Only SUPER_ADMIN can delete a business account along with all related activities, bookings, reviews, and admin links.")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @DeleteMapping("/businesses/{businessId}")
    public ResponseEntity<String> deleteBusinessBySuperAdmin(@PathVariable Long businessId,
                                                             @RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");

        try {
            businessService.delete(businessId); // ✅ this handles everything: activities, bookings, reviews, links
            return ResponseEntity.ok("Business and all related data deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body("Business not found.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to delete business: " + e.getMessage());
        }
    }

    @Operation (summary = "Disable a Business", description="Only super admins can disable a business")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/businesses/{businessId}/disable")
    public ResponseEntity<?> disableBusiness(@PathVariable Long businessId,
                                             @RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");

        try {
            Businesses business = businessService.findById(businessId);

          
            BusinessStatus inactiveStatus = businessStatusRepository.findByNameIgnoreCase("INACTIVEBYADMIN")
                .orElseThrow(() -> new RuntimeException("INACTIVE status not found in DB"));

            business.setStatus(inactiveStatus); 
            businessService.save(business);

            return ResponseEntity.ok("Business marked as INACTIVE due to low rating.");
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Business not found or status issue.");
        }
    }

    @Operation(summary = "Reactivate a Business", description = "Only SUPER_ADMIN can reactivate a disabled business")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Business reactivated successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid parameters or business not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized – Only SUPER_ADMIN allowed"),
    @ApiResponse(responseCode = "500", description = "Server error")
})
@PutMapping("/businesses/{businessId}/activate")
public ResponseEntity<?> activateBusiness(
        @PathVariable Long businessId,
        @RequestHeader("Authorization") String token) {
    // Check SUPER_ADMIN auth
    if (!isSuperAdmin(token)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
    }
    try {
        Businesses business = businessService.findById(businessId);
        if (business == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");
        }

        BusinessStatus activeStatus = businessStatusRepository.findByNameIgnoreCase("ACTIVE")
            .orElseThrow(() -> new RuntimeException("ACTIVE status not found"));

        business.setStatus(activeStatus);
        businessService.save(business);

        return ResponseEntity.ok(Map.of("message", "Business reactivated successfully", "businessId", business.getId()));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to reactivate business: " + e.getMessage()));
    }
}



}