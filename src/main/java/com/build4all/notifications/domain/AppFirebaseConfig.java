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
        @Index(name = "idx_afc_active", columnList = "is_active"),
        @Index(name = "idx_afc_provisioning_status", columnList = "provisioning_status")
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

    @Column(name = "android_config_path", length = 1000)
    private String androidConfigPath;

    @Column(name = "ios_config_path", length = 1000)
    private String iosConfigPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "provisioning_status", nullable = false, length = 30)
    private FirebaseProvisioningStatus provisioningStatus = FirebaseProvisioningStatus.PENDING;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (this.provisioningStatus == null) {
            this.provisioningStatus = FirebaseProvisioningStatus.PENDING;
        }

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}