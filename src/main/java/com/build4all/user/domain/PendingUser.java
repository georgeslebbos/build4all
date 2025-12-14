package com.build4all.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_users")
public class PendingUser {

    /**
     * PendingUser = “temporary user record” used during signup/OTP verification.
     *
     * Typical flow:
     * 1) user submits signup info -> create PendingUser + verificationCode
     * 2) user verifies OTP -> mark isVerified=true (or create real Users record and delete PendingUser)
     *
     * Why not directly Users?
     * - so unverified accounts don’t pollute the real users table
     * - you can enforce different rules (short-lived, cleanup, etc.)
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // These are optional because signup may be email-based OR phone-based OR username-based.
    // unique=true means: if value is present, it must be unique in pending_users.
    // NOTE: In some DBs, UNIQUE + NULL allows multiple NULL values (which is usually OK here).
    @Column(unique = true, nullable = true)
    private String email;

    @Column(unique = true, nullable = true)
    private String username;

    @Column(unique = true, nullable = true)
    private String phoneNumber;

    @Column(nullable = true)
    private String firstName;

    @Column(nullable = true)
    private String lastName;

    /**
     * Password is stored already hashed (BCrypt, etc.)
     * nullable=false => you can’t create a pending user without a password hash.
     */
    @Column(nullable = false)
    private String passwordHash;

    @Column(name = "profile_picture_url", nullable = true)
    private String profilePictureUrl;

    /**
     * The OTP / verification code sent to the user (SMS/email).
     * Often stored as string because it can include leading zeros.
     */
    @Column(name = "verification_code")
    private String verificationCode;

    /**
     * Flag telling whether the pending user has completed verification.
     * Default is false at Java level (and you also set it explicitly).
     */
    @Column(name = "is_verified")
    private boolean isVerified = false;

    /**
     * Status reference.
     * This is a MANY pending users -> ONE status row (ACTIVE/INACTIVE/etc.)
     * fetch=EAGER means when you load PendingUser, Hibernate will load status immediately.
     *
     * JoinColumn(name="status") means the FK column in pending_users is literally called "status"
     * and it points to UserStatus primary key (id).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status")
    private UserStatus status;

    /**
     * Profile visibility preference (nullable Boolean because you want tri-state sometimes).
     * Defaulting to true in onCreate if null.
     */
    @Column(name = "is_public_profile")
    private Boolean isPublicProfile = true;

    /**
     * created_at is set automatically on insert.
     * updatable=false => JPA will not include it in UPDATE statements.
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Lifecycle callback: called just before INSERT.
     * - sets createdAt
     * - ensures isPublicProfile is not null
     * - you intentionally do NOT set status here, because it requires DB access
     *   (repository) and entity callbacks should stay “dumb”.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isPublicProfile == null) this.isPublicProfile = true;
        // DO NOT set status here — set it in the service with userStatusRepository
    }

    // --------------------
    // Getters / Setters
    // --------------------
    public Long getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public boolean isVerified() { return isVerified; }
    public void setIsVerified(boolean isVerified) { this.isVerified = isVerified; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Boolean getIsPublicProfile() { return isPublicProfile; }
    public void setIsPublicProfile(Boolean isPublicProfile) { this.isPublicProfile = isPublicProfile; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
