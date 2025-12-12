package com.build4all.business.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.Item;
import com.build4all.review.domain.Review;
import com.build4all.role.domain.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
		  name = "businesses",
		  uniqueConstraints = {
		      @UniqueConstraint(name = "uk_biz_app_email", columnNames = {"aup_id", "email"}),
		      @UniqueConstraint(name = "uk_biz_app_phone", columnNames = {"aup_id", "phone_number"}),
		      @UniqueConstraint(name = "uk_biz_app_name", columnNames = {"aup_id", "business_name"})
		  },
		  indexes = {
		      @Index(name = "idx_biz_app", columnList = "aup_id"),
		      @Index(name = "idx_biz_email", columnList = "email"),
		      @Index(name = "idx_biz_phone", columnList = "phone_number"),
		      @Index(name = "idx_biz_status", columnList = "status"),
		      @Index(name = "idx_biz_public", columnList = "is_public_profile")
		  }
		)
public class Businesses implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aup_id", referencedColumnName = "aup_id", nullable = false)
    @JsonIgnore                    // <- add this
    private AdminUserProject ownerProjectLink;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    // IMPORTANT: remove global unique=true so duplicates across apps are allowed.
    @Column(name = "email", nullable = true)
    private String email;

    @Column(name = "phone_number", nullable = true)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "business_logo_url")
    private String businessLogoUrl;

    @Column(name = "business_banner_url")
    private String businessBannerUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "website_url")
    private String websiteUrl;

    // FK -> BusinessStatus (keep same column name)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status")
    private BusinessStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;

    @Column(name = "is_public_profile", nullable = true)
    private Boolean isPublicProfile = true;

    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Column(name = "fcm_token")
    private String fcmToken;

    @OneToMany(mappedBy = "business", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private List<Item> items;

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PendingManager> pendingManagers;

 // Businesses.java
    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore                   // ⬅️ add this
    private List<Review> reviews;


    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Businesses() {}

    public Businesses(String businessName, String email, String phoneNumber, String passwordHash,
                      String businessLogoUrl, String businessBannerUrl, String description, String websiteUrl) {
        this.businessName = businessName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.businessLogoUrl = businessLogoUrl;
        this.businessBannerUrl = businessBannerUrl;
        this.description = description;
        this.websiteUrl = websiteUrl;
    }

    /* -------- getters / setters -------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUserProject getOwnerProjectLink() { return ownerProjectLink; }
    public void setOwnerProjectLink(AdminUserProject ownerProjectLink) { this.ownerProjectLink = ownerProjectLink; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getBusinessLogoUrl() { return businessLogoUrl; }
    public void setBusinessLogoUrl(String businessLogoUrl) { this.businessLogoUrl = businessLogoUrl; }

    public String getBusinessBannerUrl() { return businessBannerUrl; }
    public void setBusinessBannerUrl(String businessBannerUrl) { this.businessBannerUrl = businessBannerUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public BusinessStatus getStatus() { return status; }
    public void setStatus(BusinessStatus status) { this.status = status; }

    public Boolean getIsPublicProfile() { return isPublicProfile; }
    public void setIsPublicProfile(Boolean isPublicProfile) { this.isPublicProfile = isPublicProfile; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getStripeAccountId() { return stripeAccountId; }
    public void setStripeAccountId(String stripeAccountId) { this.stripeAccountId = stripeAccountId; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public List<PendingManager> getPendingManagers() { return pendingManagers; }
    public void setPendingManagers(List<PendingManager> pendingManagers) { this.pendingManagers = pendingManagers; }

    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }

    public Role getRole() {
        return role;
    }

    public Boolean getPublicProfile() {
        return isPublicProfile;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setPublicProfile(Boolean publicProfile) {
        isPublicProfile = publicProfile;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDateTime.now();
        if (this.isPublicProfile == null) this.isPublicProfile = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== UserDetails implementation ==========

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null || role.getName() == null) {
            return Collections.emptyList();
        }
        String authority = "ROLE_" + role.getName().toUpperCase(); // USER -> ROLE_USER
        return List.of(new SimpleGrantedAuthority(authority));
    }

    public String getUsername() { return email; }
    public void setUsername(String email) { this.email = email; }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }


    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        if (status == null || status.getName() == null) return true;
        String s = status.getName().toUpperCase();
        return !s.equals("INACTIVE") && !s.equals("DELETED");
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return status != null && "ACTIVE".equalsIgnoreCase(status.getName());
    }
}
