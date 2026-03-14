package com.build4all.app.internaltesting.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ios_internal_testing_requests",
        indexes = {
                @Index(name = "idx_ios_itr_link", columnList = "owner_project_link_id"),
                @Index(name = "idx_ios_itr_owner", columnList = "owner_id"),
                @Index(name = "idx_ios_itr_project", columnList = "project_id"),
                @Index(name = "idx_ios_itr_email", columnList = "apple_email"),
                @Index(name = "idx_ios_itr_status", columnList = "status"),
                @Index(name = "idx_ios_itr_created", columnList = "created_at")
        }
)
public class IosInternalTestingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_project_link_id", nullable = false)
    private Long ownerProjectLinkId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "app_name_snapshot", nullable = false, length = 180)
    private String appNameSnapshot;

    @Column(name = "bundle_id_snapshot", nullable = false, length = 255)
    private String bundleIdSnapshot;

    @Column(name = "apple_email", nullable = false, length = 255)
    private String appleEmail;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private IosInternalTestingRequestStatus status = IosInternalTestingRequestStatus.REQUESTED;

    @Column(name = "apple_user_id", length = 255)
    private String appleUserId;

    @Column(name = "apple_invitation_id", length = 255)
    private String appleInvitationId;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public IosInternalTestingRequest() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.requestedAt == null) {
            this.requestedAt = now;
        }

        normalizeFields();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        normalizeFields();
    }

    private void normalizeFields() {
        if (this.appleEmail != null) {
            this.appleEmail = this.appleEmail.trim().toLowerCase();
        }
        if (this.firstName != null) {
            this.firstName = this.firstName.trim();
        }
        if (this.lastName != null) {
            this.lastName = this.lastName.trim();
        }
        if (this.appNameSnapshot != null) {
            this.appNameSnapshot = this.appNameSnapshot.trim();
        }
        if (this.bundleIdSnapshot != null) {
            this.bundleIdSnapshot = this.bundleIdSnapshot.trim();
        }
    }

    public boolean isFinalStatus() {
        return this.status != null && this.status.isFinalStatus();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerProjectLinkId() {
        return ownerProjectLinkId;
    }

    public void setOwnerProjectLinkId(Long ownerProjectLinkId) {
        this.ownerProjectLinkId = ownerProjectLinkId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getAppNameSnapshot() {
        return appNameSnapshot;
    }

    public void setAppNameSnapshot(String appNameSnapshot) {
        this.appNameSnapshot = appNameSnapshot;
    }

    public String getBundleIdSnapshot() {
        return bundleIdSnapshot;
    }

    public void setBundleIdSnapshot(String bundleIdSnapshot) {
        this.bundleIdSnapshot = bundleIdSnapshot;
    }

    public String getAppleEmail() {
        return appleEmail;
    }

    public void setAppleEmail(String appleEmail) {
        this.appleEmail = appleEmail;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public IosInternalTestingRequestStatus getStatus() {
        return status;
    }

    public void setStatus(IosInternalTestingRequestStatus status) {
        this.status = status;
    }

    public String getAppleUserId() {
        return appleUserId;
    }

    public void setAppleUserId(String appleUserId) {
        this.appleUserId = appleUserId;
    }

    public String getAppleInvitationId() {
        return appleInvitationId;
    }

    public void setAppleInvitationId(String appleInvitationId) {
        this.appleInvitationId = appleInvitationId;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(LocalDateTime readyAt) {
        this.readyAt = readyAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}