package com.build4all.notifications.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import com.build4all.notifications.domain.NotificationTypeEntity;
import com.build4all.notifications.domain.Notifications;
import com.build4all.notifications.repository.NotificationTypeRepository;
import com.build4all.notifications.repository.NotificationsRepository;
import com.build4all.webSocket.service.WebSocketEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationsService {

    private final NotificationsRepository notificationsRepo;
    private final UsersRepository usersRepo;
    private final NotificationTypeRepository notificationTypeRepo;
    private final FCMService fcmService;
    private final WebSocketEventService ws;

    public NotificationsService(NotificationsRepository notificationsRepo,
                                UsersRepository usersRepo,
                                NotificationTypeRepository notificationTypeRepo,
                                FCMService fcmService,
                                WebSocketEventService ws) {
        this.notificationsRepo = notificationsRepo;
        this.usersRepo = usersRepo;
        this.notificationTypeRepo = notificationTypeRepo;
        this.fcmService = fcmService;
        this.ws = ws;
    }

    private boolean isPushWorthy(NotificationTypeEntity type) {
        return type.getCode().equalsIgnoreCase("MESSAGE")
            || type.getCode().equalsIgnoreCase("MENTION")
            || type.getCode().equalsIgnoreCase("COMMENT")
            || type.getCode().equalsIgnoreCase("ACTIVITY_UPDATE");
    }

    public void createNotification(Users receiver, String message, String typeCode) {
        NotificationTypeEntity type = notificationTypeRepo.findByCodeIgnoreCase(typeCode)
                .orElseThrow(() -> new RuntimeException("NotificationType not found: " + typeCode));

        Notifications notification = new Notifications(receiver, message, type);
        notificationsRepo.save(notification);

        if (receiver.getFcmToken() != null && !receiver.getFcmToken().isBlank() && isPushWorthy(type)) {
            fcmService.sendNotification(receiver.getFcmToken(), "🔔 build4all", message);
        }

        if (receiver.getId() != null) {
            ws.sendUnreadBumped(receiver.getId());
        }
    }

    public void notifyBusiness(Businesses business, String message, String typeCode) {
        if (business == null || business.getId() == null) {
            throw new RuntimeException("Business is null or invalid");
        }

        NotificationTypeEntity type = notificationTypeRepo.findByCodeIgnoreCase(typeCode)
                .orElseThrow(() -> new RuntimeException("NotificationType not found: " + typeCode));

        Notifications notification = new Notifications(business, message, type);
        notificationsRepo.save(notification);

        if (business.getFcmToken() != null && !business.getFcmToken().isBlank() && isPushWorthy(type)) {
            fcmService.sendNotification(business.getFcmToken(), "📢 build4all Business", message);
        }
        // (optional) add per-business websocket queue if needed
    }

    public void notifyAdmin(AdminUser admin, String message, String typeCode) {
        NotificationTypeEntity type = notificationTypeRepo.findByCodeIgnoreCase(typeCode)
                .orElseThrow(() -> new RuntimeException("NotificationType not found: " + typeCode));

        Notifications notification = new Notifications();
        notification.setMessage(message);
        notification.setNotificationType(type);
        notification.setIsRead(false);
        notification.setCreatedAt(java.time.LocalDateTime.now());

        Users user = new Users();
        user.setId(admin.getAdminId());
        user.setUsername(admin.getUsername());
        user.setEmail(admin.getEmail());

        notification.setUser(user);
        notificationsRepo.save(notification);

        if (user.getFcmToken() != null && !user.getFcmToken().isBlank() && isPushWorthy(type)) {
            fcmService.sendNotification(user.getFcmToken(), "👑 Admin Notification", message);
        }

        if (user.getId() != null) {
            ws.sendUnreadBumped(user.getId());
        }
    }

    public List<Notifications> getAllByUser(Users user) {
        return notificationsRepo.findByUserOrderByCreatedAtDesc(user);
    }

    public List<Notifications> getUnreadByUser(Users user) {
        return notificationsRepo.findByUserAndIsReadFalse(user);
    }

    public Notifications getById(Long id) {
        return notificationsRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    public void delete(Notifications notification) {
        notificationsRepo.delete(notification);
    }

    public int countUnreadByUser(Users user) {
        return notificationsRepo.countByUserAndIsReadFalse(user);
    }

    public List<Notifications> getAllByBusiness(Businesses business) {
        return notificationsRepo.findByBusinessOrderByCreatedAtDesc(business);
    }

    public List<Notifications> getUnreadByBusiness(Businesses business) {
        return notificationsRepo.findByBusinessAndIsReadFalse(business);
    }

    @Transactional
    public void markAsRead(Long notificationId, Users user) {
        Notifications notif = getById(notificationId);
        if (notif.getUser() != null && notif.getUser().getId().equals(user.getId())) {
            notif.setIsRead(true);
            notificationsRepo.save(notif);
            ws.sendUnreadBumped(user.getId()); // update badge live
        } else {
            throw new RuntimeException("Unauthorized");
        }
    }

    @Transactional
    public void markAsReadForBusiness(Long notificationId, Businesses business) {
        Notifications notif = getById(notificationId);
        if (notif.getBusiness() != null && notif.getBusiness().getId().equals(business.getId())) {
            notif.setIsRead(true);
            notificationsRepo.save(notif);
            // (optional) add business-specific websocket bump here
        } else {
            throw new RuntimeException("Unauthorized");
        }
    }
}
