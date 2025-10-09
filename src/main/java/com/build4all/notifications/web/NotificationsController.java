package com.build4all.notifications.web;

import com.build4all.notifications.domain.Notifications;
import com.build4all.user.domain.Users;
import com.build4all.business.domain.Businesses;
import com.build4all.security.JwtUtil;
import com.build4all.notifications.service.NotificationsService;
import com.build4all.user.service.UserService;
import com.build4all.business.service.BusinessService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {

    private final NotificationsService notificationsService;
    private final UserService usersService;
    private final BusinessService businessService;

    @Autowired
    private JwtUtil jwtUtil;

    public NotificationsController(
            NotificationsService notificationsService,
            UserService usersService,
            BusinessService businessService
    ) {
        this.notificationsService = notificationsService;
        this.usersService = usersService;
        this.businessService = businessService;
    }

    // ✅ Get user notifications
    @GetMapping
    public ResponseEntity<List<Notifications>> getUserNotifications(
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Users user = usersService.getUserByEmaill(principal.getName());
        List<Notifications> notifications = notificationsService.getAllByUser(user);
        return ResponseEntity.ok(notifications != null ? notifications : Collections.emptyList());
    }

    // ✅ Mark user notification as read
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Users user = usersService.getUserByEmaill(principal.getName());
        Notifications notification = notificationsService.getById(id);

        if (notification == null || !notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.ok().build();
        }

        notificationsService.markAsRead(id, user);
        return ResponseEntity.ok().build();
    }

    // ✅ Count all user notifications
    @GetMapping("/count")
    public ResponseEntity<Long> getNotificationCount(
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Users user = usersService.getUserByEmaill(principal.getName());
        long count = notificationsService.getAllByUser(user).size();
        return ResponseEntity.ok(count);
    }

    // ✅ Count unread user notifications
    @GetMapping("/unread-count")
    public ResponseEntity<Integer> getUnreadNotificationCount(
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Users user = usersService.getUserByEmaill(principal.getName());
        int count = notificationsService.getUnreadByUser(user).size();
        return ResponseEntity.ok(count);
    }

    // ✅ Delete user notification
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Users user = usersService.getUserByEmaill(principal.getName());
        Notifications notification = notificationsService.getById(id);

        if (notification == null || !notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.ok().build();
        }

        notificationsService.delete(notification);
        return ResponseEntity.ok().build();
    }

    // ✅ Get business notifications
    @GetMapping("/business")
    public ResponseEntity<List<Notifications>> getBusinessNotifications(
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Businesses business = businessService.findByEmail(principal.getName());
        if (business == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Notifications> notifications = notificationsService.getAllByBusiness(business);
        return ResponseEntity.ok(notifications != null ? notifications : Collections.emptyList());
    }

    // ✅ Count business notifications
    @GetMapping("/business/count")
    public ResponseEntity<Long> getBusinessNotificationCount(
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Businesses business = businessService.findByEmail(principal.getName());
        if (business == null) {
            return ResponseEntity.ok(0L);
        }

        long count = notificationsService.getAllByBusiness(business).size();
        return ResponseEntity.ok(count);
    }

    // ✅ Count business unread notifications
    @GetMapping("/business/unread-count")
    public ResponseEntity<Integer> getBusinessUnreadNotificationCount(
            @RequestHeader("Authorization") String authHeader,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // principal.getName() may be email OR phone; service can handle both
        Optional<Businesses> businessOpt = businessService.findByEmailOptional(principal.getName());
        if (businessOpt.isEmpty()) {
            return ResponseEntity.ok(0);
        }

        List<Notifications> unread = notificationsService.getUnreadByBusiness(businessOpt.get());
        return ResponseEntity.ok(unread != null ? unread.size() : 0);
    }
    
    
    // ✅ Mark business notification as read
    @PutMapping("/business/{id}/read")
    public ResponseEntity<Void> markBusinessNotificationAsRead(
            @PathVariable Long id,
            Principal principal,
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Businesses business = businessService.findByEmail(principal.getName());
        if (business == null) {
            return ResponseEntity.ok().build();
        }

        Notifications notification = notificationsService.getById(id);
        if (notification == null || !notification.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.ok().build();
        }

        notificationsService.markAsReadForBusiness(id, business);
        return ResponseEntity.ok().build();
    }

    // ✅ Delete business notification
    @DeleteMapping("/business/{id}")
    public ResponseEntity<Void> deleteBusinessNotification(
            @PathVariable Long id,
            Principal principal,
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Businesses business = businessService.findByEmail(principal.getName());
        if (business == null) {
            return ResponseEntity.ok().build();
        }

        Notifications notification = notificationsService.getById(id);
        if (notification == null || !notification.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.ok().build();
        }

        notificationsService.delete(notification);
        return ResponseEntity.ok().build();
    }
    
    
    @PutMapping("/user/fcm-token")
    public ResponseEntity<Void> updateUserFcmToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> requestBody,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String fcmToken = requestBody.get("fcmToken");
        Users user = usersService.getUserByEmaill(principal.getName());

        user.setFcmToken(fcmToken);
        usersService.save(user);

        return ResponseEntity.ok().build();
    }

    
    @PutMapping("/business/fcm-token")
    public ResponseEntity<Void> updateBusinessFcmToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> requestBody,
            Principal principal) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String fcmToken = requestBody.get("fcmToken");
        Businesses business = businessService.findByEmail(principal.getName());

        business.setFcmToken(fcmToken);
        businessService.save(business);

        return ResponseEntity.ok().build();
    }

}
