package com.build4all.notifications.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;
import com.build4all.notifications.domain.NotificationTypeEntity;
import com.build4all.notifications.domain.Notifications;
import com.build4all.notifications.repository.NotificationTypeRepository;
import com.build4all.notifications.repository.NotificationsRepository;
import com.build4all.user.domain.Users;
import com.build4all.webSocket.service.WebSocketEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class NotificationsService {

    private final NotificationsRepository notificationsRepo;
    private final NotificationTypeRepository notificationTypeRepo;
    private final FCMService fcmService;
    private final WebSocketEventService ws;

    public NotificationsService(NotificationsRepository notificationsRepo,
                                NotificationTypeRepository notificationTypeRepo,
                                FCMService fcmService,
                                WebSocketEventService ws) {
        this.notificationsRepo = notificationsRepo;
        this.notificationTypeRepo = notificationTypeRepo;
        this.fcmService = fcmService;
        this.ws = ws;
    }

    /**
     * Controls which notification types should also trigger push delivery.
     * Database save always happens first and remains the source of truth.
     */
    private boolean isPushWorthy(NotificationTypeEntity type) {
        if (type == null || !StringUtils.hasText(type.getCode())) {
            return false;
        }

        String code = type.getCode().trim().toUpperCase();

        return code.equals("MESSAGE")
                || code.equals("MENTION")
                || code.equals("COMMENT")
                || code.equals("ACTIVITY_UPDATE")
                || code.equals("USER_ORDER_PLACED")
                || code.equals("PRODUCT_LOW_STOCK")
                || code.equals("PRODUCT_OUT_OF_STOCK")
                || code.equals("OWNER_NEW_PRODUCT_ANNOUNCED")
                || code.equals("OWNER_ORDER_STATUS_CHANGED")
                || code.equals("OWNER_UPGRADE_REQUEST_SENT")
                || code.equals("OWNER_SUPPORT_REQUEST_SENT")
                || code.equals("SUPER_ADMIN_REQUEST_APPROVED")
                || code.equals("SUPER_ADMIN_REQUEST_REJECTED")
                || code.equals("SUPER_ADMIN_BUILD_STARTED")
                || code.equals("SUPER_ADMIN_BUILD_SUCCEEDED")
                || code.equals("SUPER_ADMIN_BUILD_FAILED")
                || code.equals("SUPER_ADMIN_REPLIED_TO_OWNER")
                || code.equals("SUPER_ADMIN_FEATURE_ANNOUNCEMENT")
                || code.equals("NEW_REVIEW");
    }

    /**
     * Loads a notification type by code, case-insensitive.
     */
    private NotificationTypeEntity requireType(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            throw new RuntimeException("Notification type code is required");
        }

        return notificationTypeRepo.findByCodeIgnoreCase(typeCode.trim())
                .orElseThrow(() -> new RuntimeException("NotificationType not found: " + typeCode));
    }

    /**
     * Sends a push notification but never breaks DB notification creation if push fails.
     */
    private void sendPushSafely(String targetToken, String title, String body, NotificationTypeEntity type) {
        if (!StringUtils.hasText(targetToken)) {
            System.out.println("FCM skipped: target token is empty");
            return;
        }

        if (!isPushWorthy(type)) {
            System.out.println("FCM skipped: type is not push-worthy => " + type.getCode());
            return;
        }

        try {
            String response = fcmService.sendNotification(targetToken, title, body);
            System.out.println("FCM send success => " + response);
        } catch (Exception e) {
            System.out.println("FCM send failed => " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a live unread bump for a user.
     */
    private void sendUserUnreadBumpSafely(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            ws.sendUnreadBumped(userId);
        } catch (Exception ignored) {
            // Live socket updates should not break the main notification flow.
        }
    }

    /**
     * Creates a notification for a normal app user.
     */
    public void createNotification(Users receiver, String message, String typeCode) {
        if (receiver == null || receiver.getId() == null) {
            throw new RuntimeException("User receiver is null or invalid");
        }

        NotificationTypeEntity type = requireType(typeCode);

        Notifications notification = new Notifications(receiver, message, type);
        notificationsRepo.save(notification);

        sendPushSafely(receiver.getFcmToken(), "🔔 build4all", message, type);
        sendUserUnreadBumpSafely(receiver.getId());
    }

    /**
     * Transitional business notification flow kept temporarily so old code still compiles.
     * We will remove business support after controller cleanup and event migration.
     */
    public void notifyBusiness(Businesses business, String message, String typeCode) {
        if (business == null || business.getId() == null) {
            throw new RuntimeException("Business is null or invalid");
        }

        NotificationTypeEntity type = requireType(typeCode);

        Notifications notification = new Notifications(business, message, type);
        notificationsRepo.save(notification);

        sendPushSafely(business.getFcmToken(), "📢 build4all Business", message, type);
    }

    /**
     * Creates a real admin notification using the admin relation directly.
     * This replaces the old broken approach that pretended an admin was a user.
     */
    public void notifyAdmin(AdminUser admin, String message, String typeCode) {
        if (admin == null || admin.getAdminId() == null) {
            throw new RuntimeException("Admin receiver is null or invalid");
        }

        NotificationTypeEntity type = requireType(typeCode);

        Notifications notification = new Notifications(admin, message, type);
        notificationsRepo.save(notification);

        sendPushSafely(admin.getFcmToken(), "👑 build4all Admin", message, type);

        // Optional:
        // Add admin-specific websocket queue support later if your admin frontend subscribes to one.
    }

    public List<Notifications> getAllByUser(Users user) {
        return notificationsRepo.findByUserOrderByCreatedAtDesc(user);
    }

    public List<Notifications> getUnreadByUser(Users user) {
        return notificationsRepo.findByUserAndIsReadFalse(user);
    }

    public int countUnreadByUser(Users user) {
        return notificationsRepo.countByUserAndIsReadFalse(user);
    }

    public List<Notifications> getAllByAdmin(AdminUser admin) {
        return notificationsRepo.findByAdminOrderByCreatedAtDesc(admin);
    }

    public List<Notifications> getUnreadByAdmin(AdminUser admin) {
        return notificationsRepo.findByAdminAndIsReadFalse(admin);
    }

    public int countUnreadByAdmin(AdminUser admin) {
        return notificationsRepo.countByAdminAndIsReadFalse(admin);
    }

    /**
     * Transitional business methods kept temporarily so old business controller code still compiles.
     */
    public List<Notifications> getAllByBusiness(Businesses business) {
        return notificationsRepo.findByBusinessOrderByCreatedAtDesc(business);
    }

    public List<Notifications> getUnreadByBusiness(Businesses business) {
        return notificationsRepo.findByBusinessAndIsReadFalse(business);
    }

    public Notifications getById(Long id) {
        return notificationsRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    public void delete(Notifications notification) {
        notificationsRepo.delete(notification);
    }

    @Transactional
    public void markAsRead(Long notificationId, Users user) {
        Notifications notif = getById(notificationId);

        if (notif.getUser() == null || !notif.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        if (!notif.getIsRead()) {
            notif.setIsRead(true);
            notificationsRepo.save(notif);
        }

        sendUserUnreadBumpSafely(user.getId());
    }

    @Transactional
    public void markAsReadForAdmin(Long notificationId, AdminUser admin) {
        Notifications notif = getById(notificationId);

        if (notif.getAdmin() == null || !notif.getAdmin().getAdminId().equals(admin.getAdminId())) {
            throw new RuntimeException("Unauthorized");
        }

        if (!notif.getIsRead()) {
            notif.setIsRead(true);
            notificationsRepo.save(notif);
        }

        // Optional:
        // Add admin-specific websocket unread bump later if needed.
    }

    /**
     * Transitional business mark-as-read flow kept temporarily so old business controller code still compiles.
     */
    @Transactional
    public void markAsReadForBusiness(Long notificationId, Businesses business) {
        Notifications notif = getById(notificationId);

        if (notif.getBusiness() == null || !notif.getBusiness().getId().equals(business.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        if (!notif.getIsRead()) {
            notif.setIsRead(true);
            notificationsRepo.save(notif);
        }
    }
}