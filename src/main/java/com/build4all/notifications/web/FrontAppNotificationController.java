package com.build4all.notifications.web;

import com.build4all.common.errors.ApiException;
import com.build4all.notifications.domain.FrontAppNotification;
import com.build4all.notifications.domain.NotificationActorType;
import com.build4all.notifications.repository.FrontAppNotificationRepository;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/front/notifications")
public class FrontAppNotificationController {

    private final FrontAppNotificationRepository frontAppNotificationRepository;
    private final JwtUtil jwtUtil;

    public FrontAppNotificationController(
            FrontAppNotificationRepository frontAppNotificationRepository,
            JwtUtil jwtUtil
    ) {
        this.frontAppNotificationRepository = frontAppNotificationRepository;
        this.jwtUtil = jwtUtil;
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Missing or invalid Authorization header"
            );
        }
        return authHeader.substring(7);
    }

    private ActorContext resolveActorContext(String token) {
        NotificationActorType actorType;
        Long actorId;
        Long ownerProjectLinkId;

        if (jwtUtil.isUserToken(token)) {
            actorType = NotificationActorType.USER;
            actorId = jwtUtil.extractId(token);
            ownerProjectLinkId = jwtUtil.extractOwnerProjectIdForUser(token);
        } else if (jwtUtil.isOwnerToken(token)) {
            actorType = NotificationActorType.OWNER;
            actorId = jwtUtil.extractId(token);
            ownerProjectLinkId = jwtUtil.requireOwnerProjectId(token);
        } else {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Only USER or OWNER tokens can access FRONT notifications"
            );
        }

        if (actorId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ACTOR_ID_MISSING",
                    "Actor id could not be resolved from token"
            );
        }

        if (ownerProjectLinkId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OWNER_PROJECT_LINK_ID_MISSING",
                    "ownerProjectLinkId could not be resolved from token"
            );
        }

        return new ActorContext(actorType, actorId, ownerProjectLinkId);
    }

    @GetMapping
    public ResponseEntity<List<FrontAppNotification>> getMyNotifications(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = extractBearerToken(authHeader);
        ActorContext actor = resolveActorContext(token);

        List<FrontAppNotification> notifications =
                frontAppNotificationRepository
                        .findByOwnerProjectLinkIdAndReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
                                actor.ownerProjectLinkId(),
                                actor.actorType(),
                                actor.actorId()
                        );

        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = extractBearerToken(authHeader);
        ActorContext actor = resolveActorContext(token);

        int count = frontAppNotificationRepository
                .countByOwnerProjectLinkIdAndReceiverTypeAndReceiverIdAndIsReadFalse(
                        actor.ownerProjectLinkId(),
                        actor.actorType(),
                        actor.actorId()
                );

        return ResponseEntity.ok(new UnreadCountResponse(count));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<SuccessResponse> markAsRead(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long notificationId
    ) {
        if (notificationId == null || notificationId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_NOTIFICATION_ID",
                    "notificationId must be greater than 0"
            );
        }

        String token = extractBearerToken(authHeader);
        ActorContext actor = resolveActorContext(token);

        Optional<FrontAppNotification> notificationOpt =
                frontAppNotificationRepository.findByIdAndOwnerProjectLinkIdAndReceiverTypeAndReceiverId(
                        notificationId,
                        actor.ownerProjectLinkId(),
                        actor.actorType(),
                        actor.actorId()
                );

        if (notificationOpt.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "NOTIFICATION_NOT_FOUND",
                    "Notification not found"
            );
        }

        FrontAppNotification notification = notificationOpt.get();

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            frontAppNotificationRepository.save(notification);
        }

        return ResponseEntity.ok(new SuccessResponse("Notification marked as read"));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<SuccessResponse> deleteNotification(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long notificationId
    ) {
        if (notificationId == null || notificationId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_NOTIFICATION_ID",
                    "notificationId must be greater than 0"
            );
        }

        String token = extractBearerToken(authHeader);
        ActorContext actor = resolveActorContext(token);

        Optional<FrontAppNotification> notificationOpt =
                frontAppNotificationRepository.findByIdAndOwnerProjectLinkIdAndReceiverTypeAndReceiverId(
                        notificationId,
                        actor.ownerProjectLinkId(),
                        actor.actorType(),
                        actor.actorId()
                );

        if (notificationOpt.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "NOTIFICATION_NOT_FOUND",
                    "Notification not found"
            );
        }

        FrontAppNotification notification = notificationOpt.get();
        frontAppNotificationRepository.delete(notification);

        return ResponseEntity.ok(new SuccessResponse("Notification deleted successfully"));
    }

    private record ActorContext(
            NotificationActorType actorType,
            Long actorId,
            Long ownerProjectLinkId
    ) {
    }

    private record UnreadCountResponse(int unreadCount) {
    }

    private record SuccessResponse(String message) {
    }
}