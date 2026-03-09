package com.build4all.notifications.web;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.service.AdminUserService;
import com.build4all.notifications.domain.Notifications;
import com.build4all.notifications.service.NotificationsService;
import com.build4all.security.JwtUtil;
import com.build4all.user.domain.Users;
import com.build4all.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {

    private final NotificationsService notificationsService;
    private final UserService userService;
    private final AdminUserService adminUserService;
    private final JwtUtil jwtUtil;

    public NotificationsController(NotificationsService notificationsService,
                                   UserService userService,
                                   AdminUserService adminUserService,
                                   JwtUtil jwtUtil) {
        this.notificationsService = notificationsService;
        this.userService = userService;
        this.adminUserService = adminUserService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Extracts bearer token from Authorization header.
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    /**
     * Resolves the current logged-in user in a tenant-safe way using:
     * - token user id
     * - token ownerProjectId
     */
    private Users requireCurrentUser(String authHeader) {
        String token = extractBearerToken(authHeader);

        if (!jwtUtil.isUserToken(token)) {
            throw new RuntimeException("Unauthorized: user token required");
        }

        Long userId = jwtUtil.extractId(token);
        Long ownerProjectId = jwtUtil.extractOwnerProjectIdForUser(token);

        return userService.getUserById(userId, ownerProjectId);
    }

    /**
     * Resolves the current logged-in admin using adminId from token.
     * Supports OWNER and SUPER_ADMIN.
     */
    private AdminUser requireCurrentAdmin(String authHeader) {
        String token = extractBearerToken(authHeader);

        if (!jwtUtil.isAdminOrOwner(token)) {
            throw new RuntimeException("Unauthorized: owner or super admin token required");
        }

        Long adminId = jwtUtil.extractId(token);
        return adminUserService.requireById(adminId);
    }

    /* =========================================================
       USER ENDPOINTS
       ========================================================= */

    /**
     * Backward-compatible user notifications endpoint.
     * Existing frontend can still call GET /api/notifications
     */
    @GetMapping({"", "/user"})
    public ResponseEntity<?> getUserNotifications(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Users user = requireCurrentUser(authHeader);
            List<Notifications> notifications = notificationsService.getAllByUser(user);
            return ResponseEntity.ok(notifications != null ? notifications : Collections.emptyList());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    
    @PostMapping("/admin/test-push")
    public ResponseEntity<?> sendAdminTestPush(
            @RequestHeader("Authorization") String authHeader) {
        try {
            AdminUser admin = requireCurrentAdmin(authHeader);

            notificationsService.notifyAdmin(
                    admin,
                    "Test push from Build4All backend 🚀",
                    "MESSAGE"
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Test push sent successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send test push: " + e.getMessage()));
        }
    }

    /**
     * User unread count.
     */
    @GetMapping({"/unread-count", "/user/unread-count"})
    public ResponseEntity<?> getUserUnreadNotificationCount(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Users user = requireCurrentUser(authHeader);
            int count = notificationsService.countUnreadByUser(user);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * User total count.
     */
    @GetMapping({"/count", "/user/count"})
    public ResponseEntity<?> getUserNotificationCount(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Users user = requireCurrentUser(authHeader);
            long count = notificationsService.getAllByUser(user).size();
            return ResponseEntity.ok(Map.of("count", count));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Mark one user notification as read.
     */
    @PutMapping({"/{id}/read", "/user/{id}/read"})
    public ResponseEntity<?> markUserNotificationAsRead(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Users user = requireCurrentUser(authHeader);
            notificationsService.markAsRead(id, user);
            return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unable to mark notification as read";
            HttpStatus status = "Unauthorized".equalsIgnoreCase(msg) ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error", msg));
        }
    }

    /**
     * Delete one user notification.
     */
    @DeleteMapping({"/{id}", "/user/{id}"})
    public ResponseEntity<?> deleteUserNotification(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Users user = requireCurrentUser(authHeader);
            Notifications notification = notificationsService.getById(id);

            if (notification.getUser() == null || !notification.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Unauthorized"));
            }

            notificationsService.delete(notification);
            return ResponseEntity.ok(Map.of("message", "Notification deleted"));
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unable to delete notification";
            HttpStatus status = "Notification not found".equalsIgnoreCase(msg) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error", msg));
        }
    }

    /**
     * Save/update FCM token for the current user.
     */
    @PutMapping("/user/fcm-token")
    public ResponseEntity<?> updateUserFcmToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> requestBody) {
        try {
            Users user = requireCurrentUser(authHeader);
            String fcmToken = requestBody.get("fcmToken");

            user.setFcmToken(fcmToken);
            userService.save(user);

            return ResponseEntity.ok(Map.of("message", "User FCM token updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* =========================================================
       ADMIN ENDPOINTS (OWNER + SUPER_ADMIN)
       ========================================================= */

    /**
     * Get notifications for current admin.
     * Works for OWNER and SUPER_ADMIN.
     */
    @GetMapping("/admin")
    public ResponseEntity<?> getAdminNotifications(
            @RequestHeader("Authorization") String authHeader) {
        try {
            AdminUser admin = requireCurrentAdmin(authHeader);
            List<Notifications> notifications = notificationsService.getAllByAdmin(admin);
            return ResponseEntity.ok(notifications != null ? notifications : Collections.emptyList());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get unread count for current admin.
     */
    @GetMapping("/admin/unread-count")
    public ResponseEntity<?> getAdminUnreadNotificationCount(
            @RequestHeader("Authorization") String authHeader) {
        try {
            AdminUser admin = requireCurrentAdmin(authHeader);
            int count = notificationsService.countUnreadByAdmin(admin);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get total notification count for current admin.
     */
    @GetMapping("/admin/count")
    public ResponseEntity<?> getAdminNotificationCount(
            @RequestHeader("Authorization") String authHeader) {
        try {
            AdminUser admin = requireCurrentAdmin(authHeader);
            long count = notificationsService.getAllByAdmin(admin).size();
            return ResponseEntity.ok(Map.of("count", count));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Mark one admin notification as read.
     */
    @PutMapping("/admin/{id}/read")
    public ResponseEntity<?> markAdminNotificationAsRead(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            AdminUser admin = requireCurrentAdmin(authHeader);
            notificationsService.markAsReadForAdmin(id, admin);
            return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unable to mark notification as read";
            HttpStatus status = "Unauthorized".equalsIgnoreCase(msg) ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error", msg));
        }
    }

    /**
     * Delete one admin notification.
     */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<?> deleteAdminNotification(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            AdminUser admin = requireCurrentAdmin(authHeader);
            Notifications notification = notificationsService.getById(id);

            if (notification.getAdmin() == null || !notification.getAdmin().getAdminId().equals(admin.getAdminId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Unauthorized"));
            }

            notificationsService.delete(notification);
            return ResponseEntity.ok(Map.of("message", "Notification deleted"));
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unable to delete notification";
            HttpStatus status = "Notification not found".equalsIgnoreCase(msg) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(Map.of("error", msg));
        }
    }

    /**
     * Save/update FCM token for the current admin.
     * Works for OWNER and SUPER_ADMIN.
     */
    @PutMapping("/admin/fcm-token")
    public ResponseEntity<?> updateAdminFcmToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> requestBody) {
        try {
            AdminUser admin = requireCurrentAdmin(authHeader);
            String fcmToken = requestBody.get("fcmToken");

            admin.setFcmToken(fcmToken);
            adminUserService.save(admin);

            return ResponseEntity.ok(Map.of("message", "Admin FCM token updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}