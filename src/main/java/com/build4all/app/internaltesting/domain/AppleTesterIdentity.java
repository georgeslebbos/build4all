package com.build4all.app.internaltesting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "apple_tester_identity",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_apple_tester_identity_normalized_email", columnNames = "normalized_email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppleTesterIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_email", nullable = false, length = 255)
    private String normalizedEmail;

    @Column(name = "original_email", nullable = false, length = 255)
    private String originalEmail;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "apple_user_id", length = 120)
    private String appleUserId;

    @Column(name = "apple_invitation_id", length = 120)
    private String appleInvitationId;

    @Column(name = "apple_beta_tester_id", length = 120)
    private String appleBetaTesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AppleTesterIdentityStatus status;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "sync_attempts", nullable = false)
    @Builder.Default
    private Integer syncAttempts = 0;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (this.createdAt == null) {
            this.createdAt = now;
        }

        if (this.updatedAt == null) {
            this.updatedAt = now;
        }

        if (this.status == null) {
            this.status = AppleTesterIdentityStatus.NEW;
        }

        if (this.syncAttempts == null) {
            this.syncAttempts = 0;
        }

        if (this.normalizedEmail == null && this.originalEmail != null) {
            this.normalizedEmail = normalizeEmail(this.originalEmail);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();

        if (this.originalEmail != null) {
            this.normalizedEmail = normalizeEmail(this.originalEmail);
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public void markInvitationSent(String invitationId) {
        this.appleInvitationId = invitationId;
        this.status = AppleTesterIdentityStatus.INVITATION_SENT;
        this.invitedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markWaitingAcceptance() {
        this.status = AppleTesterIdentityStatus.WAITING_ACCEPTANCE;
        this.lastError = null;
    }

    public void markUserVisible(String appleUserId) {
        this.appleUserId = appleUserId;
        this.status = AppleTesterIdentityStatus.USER_VISIBLE;

        if (this.acceptedAt == null) {
            this.acceptedAt = LocalDateTime.now();
        }

        this.lastError = null;
    }

    public void markBetaTesterReady(String appleBetaTesterId) {
        this.appleBetaTesterId = appleBetaTesterId;
        this.status = AppleTesterIdentityStatus.BETA_TESTER_READY;
        this.lastError = null;

        if (this.acceptedAt == null) {
            this.acceptedAt = LocalDateTime.now();
        }
    }

    public void markFailed(String error) {
        this.status = AppleTesterIdentityStatus.FAILED;
        this.lastError = error;
    }

    public void incrementSyncAttempts() {
        if (this.syncAttempts == null) {
            this.syncAttempts = 0;
        }
        this.syncAttempts++;
        this.lastSyncedAt = LocalDateTime.now();
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNormalizedEmail() {
		return normalizedEmail;
	}

	public void setNormalizedEmail(String normalizedEmail) {
		this.normalizedEmail = normalizedEmail;
	}

	public String getOriginalEmail() {
		return originalEmail;
	}

	public void setOriginalEmail(String originalEmail) {
		this.originalEmail = originalEmail;
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

	public String getAppleBetaTesterId() {
		return appleBetaTesterId;
	}

	public void setAppleBetaTesterId(String appleBetaTesterId) {
		this.appleBetaTesterId = appleBetaTesterId;
	}

	public AppleTesterIdentityStatus getStatus() {
		return status;
	}

	public void setStatus(AppleTesterIdentityStatus status) {
		this.status = status;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public Integer getSyncAttempts() {
		return syncAttempts;
	}

	public void setSyncAttempts(Integer syncAttempts) {
		this.syncAttempts = syncAttempts;
	}

	public LocalDateTime getInvitedAt() {
		return invitedAt;
	}

	public void setInvitedAt(LocalDateTime invitedAt) {
		this.invitedAt = invitedAt;
	}

	public LocalDateTime getAcceptedAt() {
		return acceptedAt;
	}

	public void setAcceptedAt(LocalDateTime acceptedAt) {
		this.acceptedAt = acceptedAt;
	}

	public LocalDateTime getLastSyncedAt() {
		return lastSyncedAt;
	}

	public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
		this.lastSyncedAt = lastSyncedAt;
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