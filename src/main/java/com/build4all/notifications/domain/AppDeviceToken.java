package com.build4all.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "app_device_tokens",
    indexes = {
        @Index(name = "idx_adt_owner_project_link", columnList = "owner_project_link_id"),
        @Index(name = "idx_adt_actor", columnList = "actor_type, actor_id"),
        @Index(name = "idx_adt_scope", columnList = "app_scope"),
        @Index(name = "idx_adt_active", columnList = "is_active"),
        @Index(name = "idx_adt_receiver_lookup", columnList = "owner_project_link_id, app_scope, actor_type, actor_id, is_active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_adt_fcm_token", columnNames = {"fcm_token"})
    }
)

@NoArgsConstructor
public class AppDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * USER / OWNER / ADMIN
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private NotificationActorType actorType;

    /**
     * ID of the user/owner/admin in your system.
     * We keep it generic here to avoid hard coupling in step 1.
     */
    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    /**
     * FRONT or MANAGER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "app_scope", nullable = false, length = 20)
    private AppScope appScope;

    /**
     * Required for FRONT tokens.
     * Nullable for MANAGER tokens.
     */
    @Column(name = "owner_project_link_id")
    private Long ownerProjectLinkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "package_name", length = 255)
    private String packageName;

    @Column(name = "bundle_id", length = 255)
    private String bundleId;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "fcm_token", nullable = false, unique = true, columnDefinition = "TEXT")
    private String fcmToken;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.lastSeenAt == null) {
            this.lastSeenAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        this.lastSeenAt = LocalDateTime.now();
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public NotificationActorType getActorType() {
		return actorType;
	}

	public void setActorType(NotificationActorType actorType) {
		this.actorType = actorType;
	}

	public Long getActorId() {
		return actorId;
	}

	public void setActorId(Long actorId) {
		this.actorId = actorId;
	}

	public AppScope getAppScope() {
		return appScope;
	}

	public void setAppScope(AppScope appScope) {
		this.appScope = appScope;
	}

	public Long getOwnerProjectLinkId() {
		return ownerProjectLinkId;
	}

	public void setOwnerProjectLinkId(Long ownerProjectLinkId) {
		this.ownerProjectLinkId = ownerProjectLinkId;
	}

	public DevicePlatform getPlatform() {
		return platform;
	}

	public void setPlatform(DevicePlatform platform) {
		this.platform = platform;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getBundleId() {
		return bundleId;
	}

	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getFcmToken() {
		return fcmToken;
	}

	public void setFcmToken(String fcmToken) {
		this.fcmToken = fcmToken;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public LocalDateTime getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(LocalDateTime lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
    
    
}