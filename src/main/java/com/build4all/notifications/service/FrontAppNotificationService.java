package com.build4all.notifications.service;

import com.build4all.notifications.domain.AppDeviceToken;
import com.build4all.notifications.domain.AppScope;
import com.build4all.notifications.domain.FrontAppNotification;
import com.build4all.notifications.domain.FrontNotificationType;
import com.build4all.notifications.domain.NotificationActorType;
import com.build4all.notifications.repository.AppDeviceTokenRepository;
import com.build4all.notifications.repository.FrontAppNotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FrontAppNotificationService {

    private final FrontAppNotificationRepository frontAppNotificationRepository;
    private final AppDeviceTokenRepository appDeviceTokenRepository;
    private final FrontPushService frontPushService;
    private final ObjectMapper objectMapper;

    public FrontAppNotificationService(
            FrontAppNotificationRepository frontAppNotificationRepository,
            AppDeviceTokenRepository appDeviceTokenRepository,
            FrontPushService frontPushService,
            ObjectMapper objectMapper
    ) {
        this.frontAppNotificationRepository = frontAppNotificationRepository;
        this.appDeviceTokenRepository = appDeviceTokenRepository;
        this.frontPushService = frontPushService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FrontAppNotification createAndSendNotification(
            Long ownerProjectLinkId,
            NotificationActorType senderType,
            Long senderId,
            NotificationActorType receiverType,
            Long receiverId,
            FrontNotificationType notificationType,
            String title,
            String body,
            String payloadJson
    ) {
        if (ownerProjectLinkId == null) {
            throw new RuntimeException("ownerProjectLinkId is required");
        }

        if (senderType == null) {
            throw new RuntimeException("senderType is required");
        }

        if (senderId == null) {
            throw new RuntimeException("senderId is required");
        }

        if (receiverType == null) {
            throw new RuntimeException("receiverType is required");
        }

        if (receiverId == null) {
            throw new RuntimeException("receiverId is required");
        }

        if (notificationType == null) {
            throw new RuntimeException("notificationType is required");
        }

        if (!StringUtils.hasText(body)) {
            throw new RuntimeException("Notification body is required");
        }

        FrontAppNotification notification = new FrontAppNotification();
        notification.setOwnerProjectLinkId(ownerProjectLinkId);
        notification.setSenderType(senderType);
        notification.setSenderId(senderId);
        notification.setReceiverType(receiverType);
        notification.setReceiverId(receiverId);
        notification.setNotificationType(notificationType.name());
        notification.setTitle(trimToNull(title));
        notification.setBody(body.trim());
        notification.setPayloadJson(trimToNull(payloadJson));
        notification.setRead(false);

        FrontAppNotification saved = frontAppNotificationRepository.save(notification);

        List<AppDeviceToken> recipientTokens =
                appDeviceTokenRepository.findByOwnerProjectLinkIdAndAppScopeAndActorTypeAndActorIdAndIsActiveTrue(
                        ownerProjectLinkId,
                        AppScope.FRONT,
                        receiverType,
                        receiverId
                );

        if (recipientTokens.isEmpty()) {
            System.out.println("No active FRONT device tokens found for receiver => ownerProjectLinkId="
                    + ownerProjectLinkId + ", receiverType=" + receiverType + ", receiverId=" + receiverId);
            return saved;
        }

        for (AppDeviceToken deviceToken : recipientTokens) {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("type", notificationType.name());
                data.put("notificationId", String.valueOf(saved.getId()));
                data.put("ownerProjectLinkId", String.valueOf(ownerProjectLinkId));
                data.put("receiverType", receiverType.name());
                data.put("receiverId", String.valueOf(receiverId));

                if (saved.getPayloadJson() != null) {
                    data.put("payloadJson", saved.getPayloadJson());
                }

                frontPushService.sendPush(
                        ownerProjectLinkId,
                        deviceToken.getFcmToken(),
                        saved.getTitle(),
                        saved.getBody(),
                        data
                );
            } catch (Exception e) {
                System.out.println("Failed to send FRONT push for notificationId="
                        + saved.getId() + " to tokenId=" + deviceToken.getId() + " => " + e.getMessage());
            }
        }

        return saved;
    }

    /* =========================================================
       ORDER NOTIFICATION HELPERS
       ========================================================= */

    public FrontAppNotification notifyOwnerOrderCreated(
            Long ownerProjectLinkId,
            Long ownerId,
            Long userId,
            Long orderId,
            String orderCode
    ) {
        return createAndSendNotification(
                ownerProjectLinkId,
                NotificationActorType.USER,
                userId,
                NotificationActorType.OWNER,
                ownerId,
                FrontNotificationType.ORDER_CREATED,
                "New order",
                "A new order" + formatOrderSuffix(orderCode) + " was placed.",
                buildOrderPayload("order_details", orderId, orderCode, "ORDER_CREATED", null)
        );
    }

    public FrontAppNotification notifyUserOrderAccepted(
            Long ownerProjectLinkId,
            Long ownerId,
            Long userId,
            Long orderId,
            String orderCode
    ) {
        return createAndSendNotification(
                ownerProjectLinkId,
                NotificationActorType.OWNER,
                ownerId,
                NotificationActorType.USER,
                userId,
                FrontNotificationType.ORDER_ACCEPTED,
                "Order accepted",
                "Your order" + formatOrderSuffix(orderCode) + " was accepted.",
                buildOrderPayload("order_details", orderId, orderCode, "ORDER_ACCEPTED", null)
        );
    }

    public FrontAppNotification notifyUserOrderRejected(
            Long ownerProjectLinkId,
            Long ownerId,
            Long userId,
            Long orderId,
            String orderCode,
            String reason
    ) {
        String body = "Your order" + formatOrderSuffix(orderCode) + " was rejected.";
        if (StringUtils.hasText(reason)) {
            body += " Reason: " + reason.trim();
        }

        return createAndSendNotification(
                ownerProjectLinkId,
                NotificationActorType.OWNER,
                ownerId,
                NotificationActorType.USER,
                userId,
                FrontNotificationType.ORDER_REJECTED,
                "Order rejected",
                body,
                buildOrderPayload("order_details", orderId, orderCode, "ORDER_REJECTED", reason)
        );
    }

    public FrontAppNotification notifyUserOrderCanceledByOwner(
            Long ownerProjectLinkId,
            Long ownerId,
            Long userId,
            Long orderId,
            String orderCode,
            String reason
    ) {
        String body = "Your order" + formatOrderSuffix(orderCode) + " was canceled by the owner.";
        if (StringUtils.hasText(reason)) {
            body += " Reason: " + reason.trim();
        }

        return createAndSendNotification(
                ownerProjectLinkId,
                NotificationActorType.OWNER,
                ownerId,
                NotificationActorType.USER,
                userId,
                FrontNotificationType.ORDER_CANCELED_BY_OWNER,
                "Order canceled",
                body,
                buildOrderPayload("order_details", orderId, orderCode, "ORDER_CANCELED_BY_OWNER", reason)
        );
    }

    public FrontAppNotification notifyOwnerOrderCanceledByUser(
            Long ownerProjectLinkId,
            Long ownerId,
            Long userId,
            Long orderId,
            String orderCode
    ) {
        return createAndSendNotification(
                ownerProjectLinkId,
                NotificationActorType.USER,
                userId,
                NotificationActorType.OWNER,
                ownerId,
                FrontNotificationType.ORDER_CANCELED_BY_USER,
                "Order cancel request",
                "The user requested to cancel order" + formatOrderSuffix(orderCode) + ".",
                buildOrderPayload("order_details", orderId, orderCode, "ORDER_CANCELED_BY_USER", null)
        );
    }

    public FrontAppNotification notifyUserOrderStatusUpdated(
            Long ownerProjectLinkId,
            Long ownerId,
            Long userId,
            Long orderId,
            String orderCode,
            String statusCode
    ) {
        String safeStatus = StringUtils.hasText(statusCode) ? statusCode.trim().toUpperCase() : "UPDATED";

        return createAndSendNotification(
                ownerProjectLinkId,
                NotificationActorType.OWNER,
                ownerId,
                NotificationActorType.USER,
                userId,
                FrontNotificationType.ORDER_STATUS_UPDATED,
                "Order update",
                "Your order" + formatOrderSuffix(orderCode) + " status is now " + safeStatus + ".",
                buildOrderPayload("order_details", orderId, orderCode, "ORDER_STATUS_UPDATED", safeStatus)
        );
    }

    
    /* =========================================================
    STOCK NOTIFICATION HELPERS
    ========================================================= */

 public FrontAppNotification notifyOwnerLowStock(
         Long ownerProjectLinkId,
         Long ownerId,
         Long itemId,
         String itemName,
         Integer remainingStock
 ) {
     String safeName = StringUtils.hasText(itemName) ? itemName.trim() : "Item";
     String body = safeName + " is running low";
     if (remainingStock != null) {
         body += ". Remaining stock: " + remainingStock + ".";
     } else {
         body += ".";
     }

     return createAndSendNotification(
             ownerProjectLinkId,
             NotificationActorType.ADMIN,
             0L,
             NotificationActorType.OWNER,
             ownerId,
             FrontNotificationType.LOW_STOCK,
             "Low stock alert",
             body,
             buildStockPayload("inventory", itemId, safeName, remainingStock, "LOW_STOCK")
     );
 }

 public FrontAppNotification notifyOwnerOutOfStock(
         Long ownerProjectLinkId,
         Long ownerId,
         Long itemId,
         String itemName
 ) {
     String safeName = StringUtils.hasText(itemName) ? itemName.trim() : "Item";

     return createAndSendNotification(
             ownerProjectLinkId,
             NotificationActorType.ADMIN,
             0L,
             NotificationActorType.OWNER,
             ownerId,
             FrontNotificationType.OUT_OF_STOCK,
             "Out of stock",
             safeName + " is now out of stock.",
             buildStockPayload("inventory", itemId, safeName, 0, "OUT_OF_STOCK")
     );
 }
 
 private String buildStockPayload(
         String screen,
         Long itemId,
         String itemName,
         Integer remainingStock,
         String event
 ) {
     Map<String, Object> payload = new LinkedHashMap<>();
     payload.put("screen", screen);
     payload.put("itemId", itemId);
     payload.put("itemName", itemName);
     payload.put("remainingStock", remainingStock);
     payload.put("event", event);

     try {
         return objectMapper.writeValueAsString(payload);
     } catch (JsonProcessingException e) {
         throw new RuntimeException("Failed to build stock notification payload", e);
     }
 }
    /* =========================================================
       INTERNAL HELPERS
       ========================================================= */

    private String formatOrderSuffix(String orderCode) {
        if (!StringUtils.hasText(orderCode)) {
            return "";
        }
        return " #" + orderCode.trim();
    }

    private String buildOrderPayload(
            String screen,
            Long orderId,
            String orderCode,
            String event,
            String extra
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("screen", screen);
        payload.put("orderId", orderId);
        payload.put("orderCode", orderCode);
        payload.put("event", event);
        payload.put("extra", extra);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build order notification payload", e);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}