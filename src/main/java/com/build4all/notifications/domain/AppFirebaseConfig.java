package com.build4all.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "app_firebase_config",
    indexes = {
        @Index(name = "idx_afc_owner_project_link", columnList = "owner_project_link_id", unique = true),
        @Index(name = "idx_afc_active", columnList = "is_active")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AppFirebaseConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The real routing/business key for the front app.
     * This should match your linkId / ownerProjectLinkId.
     */
    @Column(name = "owner_project_link_id", nullable = false, unique = true)
    private Long ownerProjectLinkId;

    @Column(name = "firebase_project_id", nullable = false, length = 255)
    private String firebaseProjectId;

    @Column(name = "firebase_project_name", length = 255)
    private String firebaseProjectName;

    @Column(name = "android_package_name", length = 255)
    private String androidPackageName;

    @Column(name = "ios_bundle_id", length = 255)
    private String iosBundleId;

    /**
     * Better to store a secret reference/path than raw JSON directly.
     * Example: vault key / encrypted file path / secret manager key.
     */
    @Column(name = "service_account_secret_ref", nullable = false, length = 500)
    private String serviceAccountSecretRef;

    @Column(name = "android_firebase_app_id", length = 255)
    private String androidFirebaseAppId;

    @Column(name = "ios_firebase_app_id", length = 255)
    private String iosFirebaseAppId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

	public String getFirebaseProjectId() {
		return firebaseProjectId;
	}

	public void setFirebaseProjectId(String firebaseProjectId) {
		this.firebaseProjectId = firebaseProjectId;
	}

	public String getFirebaseProjectName() {
		return firebaseProjectName;
	}

	public void setFirebaseProjectName(String firebaseProjectName) {
		this.firebaseProjectName = firebaseProjectName;
	}

	public String getAndroidPackageName() {
		return androidPackageName;
	}

	public void setAndroidPackageName(String androidPackageName) {
		this.androidPackageName = androidPackageName;
	}

	public String getIosBundleId() {
		return iosBundleId;
	}

	public void setIosBundleId(String iosBundleId) {
		this.iosBundleId = iosBundleId;
	}

	public String getServiceAccountSecretRef() {
		return serviceAccountSecretRef;
	}

	public void setServiceAccountSecretRef(String serviceAccountSecretRef) {
		this.serviceAccountSecretRef = serviceAccountSecretRef;
	}

	public String getAndroidFirebaseAppId() {
		return androidFirebaseAppId;
	}

	public void setAndroidFirebaseAppId(String androidFirebaseAppId) {
		this.androidFirebaseAppId = androidFirebaseAppId;
	}

	public String getIosFirebaseAppId() {
		return iosFirebaseAppId;
	}

	public void setIosFirebaseAppId(String iosFirebaseAppId) {
		this.iosFirebaseAppId = iosFirebaseAppId;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
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