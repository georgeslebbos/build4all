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

/**
 * Businesses (tenant-aware)
 * ------------------------
 * Represents a Business account inside ONE tenant/app (AdminUserProject aup_id).
 *
 * Key idea:
 * - "businesses" rows are scoped by aup_id (ownerProjectLink).
 * - email/phone/name uniqueness is enforced per aup_id, not globally.
 *
 * Implements UserDetails so Spring Security can put this entity as the authenticated principal
 * when the JWT role is BUSINESS (or similar).
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // avoids Jackson errors when serializing Hibernate proxies
@Entity
@Table(
        name = "businesses",
        uniqueConstraints = {
                // Unique inside the same tenant/app (aup_id)
                // Equivalent SQL:
                //   UNIQUE (aup_id, email)
                @UniqueConstraint(name = "uk_biz_app_email", columnNames = {"aup_id", "email"}),

                // Equivalent SQL:
                //   UNIQUE (aup_id, phone_number)
                @UniqueConstraint(name = "uk_biz_app_phone", columnNames = {"aup_id", "phone_number"}),

                // Equivalent SQL:
                //   UNIQUE (aup_id, business_name)
                @UniqueConstraint(name = "uk_biz_app_name", columnNames = {"aup_id", "business_name"})
        },
        indexes = {
                // Helpful for tenant-scoped queries: WHERE aup_id = ?
                // Equivalent SQL:
                //   CREATE INDEX idx_biz_app ON businesses(aup_id);
                @Index(name = "idx_biz_app", columnList = "aup_id"),

                @Index(name = "idx_biz_email", columnList = "email"),
                @Index(name = "idx_biz_phone", columnList = "phone_number"),
                @Index(name = "idx_biz_status", columnList = "status"),
                @Index(name = "idx_biz_public", columnList = "is_public_profile")
        }
)
public class Businesses implements UserDetails {

    /**
     * Primary key for the business row.
     * Column name business_id matches the physical DB column.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_id")
    private Long id;

    /**
     * Tenant link (owner + project) = AdminUserProject.
     * Many businesses belong to ONE tenant/app (same aup_id).
     *
     * LAZY because we rarely need to load the full AdminUserProject when loading a Business.
     * optional=false + nullable=false enforces: every Business must belong to a tenant.
     *
     * NOTE: referencedColumnName="aup_id" means the FK references AdminUserProject.aup_id
     * (not necessarily the "id" column).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aup_id", referencedColumnName = "aup_id", nullable = false)
    @JsonIgnore                    // <- add this (prevents recursion + leaking tenant metadata in API)
    private AdminUserProject ownerProjectLink;

    /**
     * Business display name (required).
     * Uniqueness is enforced per tenant via uk_biz_app_name (aup_id + business_name).
     */
    @Column(name = "business_name", nullable = false)
    private String businessName;

    /**
     * IMPORTANT: remove global unique=true so duplicates across apps are allowed.
     * Email can repeat in a different aup_id (different app/tenant),
     * but within the same app it must be unique due to uk_biz_app_email (aup_id, email).
     */
    @Column(name = "email", nullable = true)
    private String email;

    /**
     * Same idea as email:
     * - can repeat across different apps
     * - unique inside same app by uk_biz_app_phone (aup_id, phone_number)
     */
    @Column(name = "phone_number", nullable = true)
    private String phoneNumber;

    /**
     * Hashed password (BCrypt recommended).
     * Never store raw password.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Optional branding images. Typically stored as URL paths (/uploads/...) or full URLs. */
    @Column(name = "business_logo_url")
    private String businessLogoUrl;

    @Column(name = "business_banner_url")
    private String businessBannerUrl;

    /** Long description - TEXT type in DB (Postgres: TEXT, MySQL: TEXT). */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "website_url")
    private String websiteUrl;

    /**
     * FK -> BusinessStatus (keep same column name)
     * Column "status" typically stores business_status.id.
     *
     * EAGER because status is small and is frequently needed for enabled/locked checks in security.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status")
    private BusinessStatus status;

    /**
     * Security role FK -> Role table.
     * Used by getAuthorities() to produce ROLE_<NAME>.
     *
     * OnDelete CASCADE means: if role row is deleted, DB will delete this Business row too.
     * (Be careful: deleting a role can delete many users/businesses!)
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;

    /**
     * Public profile flag (nullable in DB, but we default to true in Java).
     * Used to decide whether the business is visible in public lists.
     */
    @Column(name = "is_public_profile", nullable = true)
    private Boolean isPublicProfile = true;

    /**
     * Stripe connected account id (for payouts / marketplace style).
     * Example: acct_12345...
     */
    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    /** Firebase Cloud Messaging token for push notifications. */
    @Column(name = "fcm_token")
    private String fcmToken;

    /**
     * One business can own many items.
     * cascade=REMOVE ensures deleting a business removes its items.
     * orphanRemoval=true ensures items removed from the collection are deleted too.
     */
    @OneToMany(mappedBy = "business", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore // avoid huge payloads + recursion
    private List<Item> items;

    /**
     * Pending managers invited/awaiting approval for this business.
     * cascade=ALL because we treat these children as part of the business aggregate.
     */
    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PendingManager> pendingManagers;

    // Businesses.java
    /**
     * Reviews left on this business.
     * @JsonIgnore avoids circular references (Review -> Business -> Reviews -> ...)
     */
    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore                   // ⬅️ add this
    private List<Review> reviews;

    /** Last login timestamp (useful for analytics, security audits). */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Created timestamp (immutable after insert). */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Updated timestamp (changes on each update). */
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

    /**
     * Auto-set timestamps on INSERT.
     * Also ensures isPublicProfile is not null (defaults to true).
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDateTime.now();
        if (this.isPublicProfile == null) this.isPublicProfile = true;
    }

    /** Auto-update updatedAt on UPDATE. */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== UserDetails implementation ==========

    /**
     * Converts DB Role -> Spring Security authority.
     * Example:
     *   role.name = "BUSINESS"  => "ROLE_BUSINESS"
     *   role.name = "OWNER"     => "ROLE_OWNER"
     */
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null || role.getName() == null) {
            return Collections.emptyList();
        }
        String authority = "ROLE_" + role.getName().toUpperCase(); // USER -> ROLE_USER
        return List.of(new SimpleGrantedAuthority(authority));
    }

    /**
     * Spring Security "username" for this principal.
     *
     * ⚠️ Note:
     * - You're using email as username here.
     * - If a business logs in via phone, your JWT subject might be phone instead.
     *   In that case, consider changing this to return (email != null ? email : phoneNumber),
     *   OR override your auth flow to always set subject consistently.
     */
    public String getUsername() { return email; }
    public void setUsername(String email) { this.email = email; }

    /** Spring Security password field (hashed). */
    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true; // implement expiration logic if needed
    }

    /**
     * Locks account if status is INACTIVE or DELETED.
     * BusinessStatus is used here (not UserStatus).
     */
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
        return true; // implement credential expiry if needed
    }

    /**
     * Enabled only when status is ACTIVE.
     * This affects Spring Security authorization checks.
     */
    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return status != null && "ACTIVE".equalsIgnoreCase(status.getName());
    }
}
