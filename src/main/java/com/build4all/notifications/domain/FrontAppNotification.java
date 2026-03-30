package com.build4all.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "front_app_notifications",
    indexes = {
        @Index(name = "idx_fan_owner_project_link", columnList = "owner_project_link_id"),
        @Index(name = "idx_fan_receiver", columnList = "receiver_type, receiver_id"),
        @Index(name = "idx_fan_unread_lookup", columnList = "owner_project_link_id, receiver_type, receiver_id, is_read"),
        @Index(name = "idx_fan_created_at", columnList = "created_at")
    }
)

@NoArgsConstructor
public class FrontAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Main business scope of the front app.
     */
    @Column(name = "owner_project_link_id", nullable = false)
    private Long ownerProjectLinkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private NotificationActorType senderType;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "receiver_type", nullable = false, length = 20)
    private NotificationActorType receiverType;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /**
     * Example: MESSAGE / ORDER_UPDATE / BOOKING_STATUS / REVIEW / etc.
     */
    @Column(name = "notification_type", nullable = false, length = 100)
    private String notificationType;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * Optional JSON payload for app navigation / metadata.
     */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getOwnerProjectLinkId() {
		return ownerProjectLinkId;
	}

	public void setOwnerProjectLinkId(Long ownerProjectLinkId) {
		this.ownerProjectLinkId = ownerProjectLinkId;
	}

	public NotificationActorType getSenderType() {
		return senderType;
	}

	public void setSenderType(NotificationActorType senderType) {
		this.senderType = senderType;
	}

	public Long getSenderId() {
		return senderId;
	}

	public void setSenderId(Long senderId) {
		this.senderId = senderId;
	}

	public NotificationActorType getReceiverType() {
		return receiverType;
	}

	public void setReceiverType(NotificationActorType receiverType) {
		this.receiverType = receiverType;
	}

	public Long getReceiverId() {
		return receiverId;
	}

	public void setReceiverId(Long receiverId) {
		this.receiverId = receiverId;
	}

	public String getNotificationType() {
		return notificationType;
	}

	public void setNotificationType(String notificationType) {
		this.notificationType = notificationType;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public void setPayloadJson(String payloadJson) {
		this.payloadJson = payloadJson;
	}

	public boolean isRead() {
		return isRead;
	}

	public void setRead(boolean isRead) {
		this.isRead = isRead;
	}

	public LocalDateTime getReadAt() {
		return readAt;
	}

	public void setReadAt(LocalDateTime readAt) {
		this.readAt = readAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
    
    
}