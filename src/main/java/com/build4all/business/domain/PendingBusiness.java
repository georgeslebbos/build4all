package com.build4all.business.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_businesses")
public class PendingBusiness {

    /* =========================================================
     * PendingBusiness
     * ---------------------------------------------------------
     * Temporary table used during multi-step business registration.
     * Typical flow:
     * 1) Create/update PendingBusiness with email/phone + passwordHash + verificationCode
     * 2) Verify code  -> mark isVerified=true
     * 3) Complete profile (name/description/logo/banner...) then create real Businesses row
     * 4) Delete PendingBusiness record
     * ========================================================= */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB auto-increment/identity PK
    private Long id;

    /**
     * Email used for login/verification.
     * NOTE: "unique=true" here makes email globally unique across ALL apps/tenants.
     * If you want tenant-scoped uniqueness (recommended in Build4All),
     * remove unique=true and add (aup_id, email) uniqueness like Businesses.
     */
    @Column(unique = true, nullable = true)
    private String email;

    /**
     * Hashed password (BCrypt).
     * IMPORTANT: never store raw passwords.
     * Recommended: add @Column(name="password_hash") to be consistent with other tables.
     */
    private String passwordHash;

    /**
     * Business name (can be collected in step 2 later).
     * Column is nullable to support the multi-step registration flow.
     */
    @Column(name = "business_name", nullable = true) // ✅ allow step 2 to complete later
    private String businessName;

    /**
     * Long description / about.
     * Stored as TEXT; nullable because it's filled after verification in step 2.
     */
    @Column(columnDefinition = "TEXT", nullable = true) // ✅ allow filling later
    private String description;

    /**
     * Phone number used for login/verification.
     * NOTE: "unique=true" here makes phone globally unique across ALL apps/tenants.
     * If you want tenant-scoped uniqueness, remove unique=true and make (aup_id, phone_number) unique.
     */
    @Column(name = "phone_number", nullable = true, unique = true)
    private String phoneNumber;

    /**
     * Optional website link.
     */
    @Column(name = "website_url", nullable = true) // ✅ optional at first
    private String websiteUrl;

    /**
     * Optional media URLs (set later, step 2).
     * Usually stored as "/uploads/<file>" or full CDN URL.
     */
    @Column(name = "business_logo_url", nullable = true) // ✅ will be set in step 2
    private String businessLogoUrl;

    @Column(name = "business_banner_url", nullable = true) // ✅ will be set in step 2
    private String businessBannerUrl;

    /**
     * Verification code (email OTP / SMS OTP).
     * Example: "123456"
     */
    @Column(name = "verification_code")
    private String verificationCode;

    /**
     * True after the user enters the correct verification code.
     * This flag prevents completing registration without verification.
     */
    @Column(name = "is_verified")
    private boolean isVerified = false;

    /**
     * Public profile flag.
     * Default TRUE so a business is visible by default (can be changed later).
     */
    @Column(name = "is_public_profile")
    private Boolean isPublicProfile = true;

    /**
     * Status FK -> business_status.id
     * Using EAGER since it's tiny lookup data and often needed.
     *
     * Equivalent SQL concept:
     *   pending_businesses.status -> business_status.id
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status", referencedColumnName = "id")
    private BusinessStatus status;

    /**
     * Creation timestamp.
     * Recommended: set automatically with @PrePersist like your other entities.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /* =========================================================
     * Lifecycle hooks (recommended)
     * ========================================================= */

    @PrePersist
    protected void onCreate() {
        // Auto set createdAt for new pending rows
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();

        // Avoid null surprises
        if (this.isPublicProfile == null) this.isPublicProfile = true;
    }

    /* =========================================================
     * Getters and Setters
     * ========================================================= */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public String getBusinessLogoUrl() { return businessLogoUrl; }
    public void setBusinessLogoUrl(String businessLogoUrl) { this.businessLogoUrl = businessLogoUrl; }

    public String getBusinessBannerUrl() { return businessBannerUrl; }
    public void setBusinessBannerUrl(String businessBannerUrl) { this.businessBannerUrl = businessBannerUrl; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public boolean isVerified() { return isVerified; }
    public void setIsVerified(boolean isVerified) { this.isVerified = isVerified; }

    public Boolean getIsPublicProfile() { return isPublicProfile; }
    public void setIsPublicProfile(Boolean isPublicProfile) { this.isPublicProfile = isPublicProfile; }

    public BusinessStatus getStatus() { return status; }
    public void setStatus(BusinessStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
