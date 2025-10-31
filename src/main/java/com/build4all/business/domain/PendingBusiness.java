package com.build4all.business.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_businesses")
public class PendingBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true,nullable=true)
    private String email;

    private String passwordHash;

    @Column(name = "business_name", nullable = true) // ✅ allow step 2 to complete later
    private String businessName;

    @Column(columnDefinition = "TEXT", nullable = true) // ✅ allow filling later
    private String description;

    @Column(name = "phone_number", nullable = true,unique =true)
    private String phoneNumber;

    @Column(name = "website_url", nullable = true) // ✅ optional at first
    private String websiteUrl;

    @Column(name = "business_logo_url", nullable = true) // ✅ will be set in step 2
    private String businessLogoUrl;

    @Column(name = "business_banner_url", nullable = true) // ✅ will be set in step 2
    private String businessBannerUrl;

    @Column(name = "verification_code")
    private String verificationCode;
    
    @Column(name = "is_verified")
    private boolean isVerified = false;

    @Column(name = "is_public_profile")
    private Boolean isPublicProfile = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status", referencedColumnName = "id")
    private BusinessStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // --- Getters and Setters ---

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

    public boolean isVerified() {
        return isVerified;
    }

    public void setIsVerified(boolean isVerified) {
        this.isVerified = isVerified;
    }
    
    public Boolean getIsPublicProfile() { return isPublicProfile; }
    public void setIsPublicProfile(Boolean isPublicProfile) { this.isPublicProfile = isPublicProfile; }

    public BusinessStatus getStatus() { return status; }
    public void setStatus(BusinessStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}