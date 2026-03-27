package com.build4all.app.internaltesting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ios_internal_testing_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Column(name = "app_name_snapshot", nullable = false, length = 255)
    private String appNameSnapshot;

    @Column(name = "bundle_id_snapshot", nullable = false, length = 255)
    private String bundleIdSnapshot;

    @Column(name = "apple_email", nullable = false, length = 255)
    private String appleEmail;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private IosInternalTestingRequestStatus status;

    @Column(name = "apple_user_id", length = 120)
    private String appleUserId;

    @Column(name = "apple_invitation_id", length = 120)
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "apple_tester_identity_id",
            foreignKey = @ForeignKey(name = "fk_ios_internal_testing_request_identity")
    )
    private AppleTesterIdentity appleTesterIdentity;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (this.createdAt == null) {
            this.createdAt = now;
        }

        if (this.updatedAt == null) {
            this.updatedAt = now;
        }

        if (this.requestedAt == null) {
            this.requestedAt = now;
        }

        if (this.status == null) {
            this.status = IosInternalTestingRequestStatus.REQUESTED;
        }

        if (this.appleEmail != null) {
            this.appleEmail = normalizeEmail(this.appleEmail);
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

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();

        if (this.appleEmail != null) {
            this.appleEmail = normalizeEmail(this.appleEmail);
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

    public Long getAppleTesterIdentityId() {
        return appleTesterIdentity != null ? appleTesterIdentity.getId() : null;
    }

    public boolean isFinalStatus() {
        return status == IosInternalTestingRequestStatus.READY
                || status == IosInternalTestingRequestStatus.FAILED
                || status == IosInternalTestingRequestStatus.CANCELLED;
    }

    public boolean isWaitingStatus() {
        return status == IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM
                || status == IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE
                || status == IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC
                || status == IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
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

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public AppleTesterIdentity getAppleTesterIdentity() {
		return appleTesterIdentity;
	}

	public void setAppleTesterIdentity(AppleTesterIdentity appleTesterIdentity) {
		this.appleTesterIdentity = appleTesterIdentity;
	}
    
    
    
}