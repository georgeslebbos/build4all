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

@RestController
@RequestMapping("/api/users")
public class UsersController {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsersRepository usersRepository;
    @Autowired private UserStatusRepository userStatusRepository;

    private final UserService userService;
    public UsersController(UserService userService) { this.userService = userService; }

    /* -------- list by app -------- */
    @ApiResponses({@ApiResponse(responseCode="200"),@ApiResponse(responseCode="401"),@ApiResponse(responseCode="403")})
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                         @RequestParam Long adminId,
                                         @RequestParam Long projectId) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }
            token = token.substring(7).trim();

            String role = jwtUtil.extractRole(token);
            if (jwtUtil.isBusinessToken(token) || "SUPER_ADMIN".equalsIgnoreCase(role) || role == null || "USER".equalsIgnoreCase(role)) {
                return ResponseEntity.ok(userService.getAllUserDtos(adminId, projectId));
            }
            return ResponseEntity.status(403).body("Access denied");
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid or expired token");
        }
    }

    /* -------- delete (unchanged semantics) -------- */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id,
                                             @RequestBody Map<String, String> body,
                                             @RequestHeader("Authorization") String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body("Missing or invalid token");
            }
            token = token.substring(7).trim();

            String contact = jwtUtil.extractUsername(token);
            Users acting = usersRepository.findByEmail(contact);
            if (acting == null) acting = usersRepository.findByPhoneNumber(contact);

            String role = jwtUtil.extractRole(token);
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
                boolean deleted = userService.deleteUserById(id);
                return deleted ? ResponseEntity.ok("User deleted by SUPER_ADMIN successfully")
                               : ResponseEntity.status(404).body("User not found");
            }

            if (acting == null || !Objects.equals(acting.getId(), id)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            String password = body.get("password");
            if (password == null || password.isEmpty()) return ResponseEntity.badRequest().body("Password is required");

            boolean deleted = userService.deleteUserByIdWithPassword(id, password);
            return deleted ? ResponseEntity.ok("User deleted successfully")
                           : ResponseEntity.status(403).body("Invalid password or user not found");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    /* -------- password reset (app-scoped) -------- */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> sendResetCode(@RequestBody Map<String, String> body,
                                                             @RequestParam Long adminId,
                                                             @RequestParam Long projectId) {
        try {
            String email = body.get("email");
            boolean ok = userService.resetPassword(email, adminId, projectId);
            return ok ? ResponseEntity.ok(Map.of("message", "Reset code sent"))
                      : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyCode(@RequestBody Map<String, String> body,
                                                          @RequestParam Long adminId,
                                                          @RequestParam Long projectId) {
        try {
            String email = body.get("email");
            String code  = body.get("code");
            return userService.verifyResetCode(email, code, adminId, projectId)
                    ? ResponseEntity.ok(Map.of("message", "Code verified successfully"))
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid code"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    @PostMapping("/update-password")
    public ResponseEntity<Map<String, String>> updatePassword(@RequestBody Map<String, String> body,
                                                              @RequestParam Long adminId,
                                                              @RequestParam Long projectId) {
        try {
            String email = body.get("email");
            String code  = body.get("code");
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "New password is required"));
            }
            boolean ok = userService.updatePassword(email, code, newPassword, adminId, projectId);
            return ok ? ResponseEntity.ok(Map.of("message", "Password updated successfully"))
                      : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid code or user"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Unexpected error"));
        }
    }

    /* -------- misc endpoints kept; add adminId/projectId where lookups happen if needed -------- */

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id,
                                               @RequestParam Long adminId,
                                               @RequestParam Long projectId) {
        try {
            Users user = userService.getUserById(id, adminId, projectId);
            return ResponseEntity.ok(new UserDto(user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).build();
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing or invalid token");
        }
        String contact = jwtUtil.extractUsername(token.substring(7).trim());
        Users user = usersRepository.findByEmail(contact);
        if (user == null) user = usersRepository.findByPhoneNumber(contact);

        if (user == null || !Objects.equals(user.getId(), id)) return ResponseEntity.status(403).body("Access denied");

        String statusStr = body.get("status");
        String password  = body.get("password");
        if (statusStr == null || statusStr.isBlank()) return ResponseEntity.badRequest().body("Status is required");

        if ("INACTIVE".equalsIgnoreCase(statusStr)) {
            if (password == null || password.isBlank()) return ResponseEntity.badRequest().body("Password is required to deactivate account.");
            if (!userService.checkPassword(user, password)) return ResponseEntity.status(401).body("Incorrect password. Status not changed.");
        }

        Optional<UserStatus> newStatusOpt = userStatusRepository.findByNameIgnoreCase(statusStr);
        if (newStatusOpt.isEmpty()) return ResponseEntity.badRequest().body("Invalid status value");

        user.setStatus(newStatusOpt.get());
        user.setUpdatedAt(LocalDateTime.now());
        usersRepository.save(user);
        return ResponseEntity.ok("User status updated to " + newStatusOpt.get().getName());
    }
}
