package com.build4all.notifications.web;

import com.build4all.common.errors.ApiException;
import com.build4all.notifications.domain.FrontAppNotification;
import com.build4all.notifications.domain.FrontNotificationType;
import com.build4all.notifications.domain.NotificationActorType;
import com.build4all.notifications.dto.FrontCreateNotificationTestRequest;
import com.build4all.notifications.service.FrontAppNotificationService;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/front/notifications/debug")
public class FrontNotificationDebugController {

    private final FrontAppNotificationService frontAppNotificationService;
    private final JwtUtil jwtUtil;

    public FrontNotificationDebugController(
            FrontAppNotificationService frontAppNotificationService,
            JwtUtil jwtUtil
    ) {
        this.frontAppNotificationService = frontAppNotificationService;
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
                    "Only USER or OWNER tokens can create FRONT test notifications"
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

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> createAndSendTestNotification(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody FrontCreateNotificationTestRequest request
    ) {
        String token = extractBearerToken(authHeader);
        ActorContext actor = resolveActorContext(token);

        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REQUEST_BODY_REQUIRED",
                    "Request body is required"
            );
        }

        if (request.getReceiverId() == null || request.getReceiverId() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RECEIVER_ID_REQUIRED",
                    "receiverId is required and must be greater than 0"
            );
        }

        if (request.getReceiverType() == null || request.getReceiverType().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RECEIVER_TYPE_REQUIRED",
                    "receiverType is required"
            );
        }

        if (request.getNotificationType() == null || request.getNotificationType().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NOTIFICATION_TYPE_REQUIRED",
                    "notificationType is required"
            );
        }

        if (request.getBody() == null || request.getBody().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BODY_REQUIRED",
                    "body is required"
            );
        }

        final NotificationActorType receiverType;
        try {
            receiverType = NotificationActorType.valueOf(request.getReceiverType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RECEIVER_TYPE",
                    "receiverType must be one of: USER, OWNER, ADMIN"
            );
        }

        final FrontNotificationType notificationType;
        try {
            notificationType = FrontNotificationType.valueOf(request.getNotificationType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_NOTIFICATION_TYPE",
                    "notificationType value is invalid"
            );
        }

        FrontAppNotification saved = frontAppNotificationService.createAndSendNotification(
                actor.ownerProjectLinkId(),
                actor.actorType(),
                actor.actorId(),
                receiverType,
                request.getReceiverId(),
                notificationType,
                request.getTitle(),
                request.getBody(),
                request.getPayloadJson()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Test notification created and sent successfully",
                "notificationId", saved.getId(),
                "notificationType", saved.getNotificationType()
        ));
    }

    private record ActorContext(
            NotificationActorType actorType,
            Long actorId,
            Long ownerProjectLinkId
    ) {
    }
}