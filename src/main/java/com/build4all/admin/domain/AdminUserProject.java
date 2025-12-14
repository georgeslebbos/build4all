package com.build4all.admin.domain;

import com.build4all.catalog.domain.Currency;
import com.build4all.project.domain.Project;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "admin_user_projects",
        uniqueConstraints = {
                // Enforces that the same admin cannot have the same slug twice for the same project.
                // (admin_id + project_id + slug) must be unique.
                @UniqueConstraint(name = "uk_aup_owner_project_slug", columnNames = {"admin_id", "project_id", "slug"})
        },
        indexes = {
                // These indexes speed up common filters: by admin, by project, and by slug.
                @Index(name = "idx_aup_admin", columnList = "admin_id"),
                @Index(name = "idx_aup_project", columnList = "project_id"),
                @Index(name = "idx_aup_slug", columnList = "slug")
        }
)
/**
 * AdminUserProject is the "link" (association) entity between AdminUser and Project.
 * It represents one app/tenant instance owned/managed by an admin for a specific project.
 *
 * Besides the relationship itself, it stores metadata used by the platform:
 * - licensing (licenseId, validFrom, endTo, status)
 * - routing/tenant key (slug)
 * - branding/build outputs (appName, logoUrl, apkUrl, ipaUrl, bundleUrl)
 * - UI configuration (themeId)
 * - currency override (currency)
 */
public class AdminUserProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aup_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", referencedColumnName = "admin_id", nullable = false)
    // Prevent infinite recursion / huge payload when serializing entities to JSON.
    @JsonIgnore
    private AdminUser admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", referencedColumnName = "id", nullable = false)
    // Same recursion reason as above.
    @JsonIgnore
    private Project project;

    // External or internal identifier for the subscription/license for this app.
    @Column(name = "license_id")
    private String licenseId;

    // Start date of validity (license/subscription).
    @Column(name = "valid_from")
    private LocalDate validFrom;

    // End date of validity (license/subscription).
    @Column(name = "end_to")
    private LocalDate endTo;

    @Column(name = "status", length = 16)
    // Current state of this link/app instance.
    private String status = "ACTIVE"; // ACTIVE | SUSPENDED | EXPIRED | DELETED

    @Column(name = "slug", length = 128)
    // Slug is a human-friendly identifier commonly used in routing or tenant discovery.
    private String slug;

    @Column(name = "app_name", length = 128)
    // Display name of the generated app for this link.
    private String appName;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    // URL for the app logo (stored as TEXT to support long CDN/signed URLs).
    private String logoUrl;

    @Column(name = "apk_url", columnDefinition = "TEXT")
    // URL of latest Android build output for this app instance.
    private String apkUrl;

    @Column(name = "ipa_url", columnDefinition = "TEXT")
    // URL of latest iOS build output for this app instance.
    private String ipaUrl;

    @Column(name = "bundle_url", columnDefinition = "TEXT")
    // URL of another build artifact type (e.g., AAB bundle / web bundle, depending on your pipeline).
    private String bundleUrl;

    /** Theme chosen for this app (nullable => fallback) */
    @Column(name = "theme_id")
    // Theme is stored as an ID (not as a relation) so the app can reference a theme record/config by id.
    private Long themeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", referencedColumnName = "currency_id")
    // Currency that should be used for this app instance (optional override).
    private Currency currency;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public AdminUserProject() {}

    public AdminUserProject(AdminUser admin,
                            Project project,
                            String licenseId,
                            LocalDate validFrom,
                            LocalDate endTo,
                            String status,
                            String slug,
                            String appName,
                            String apkUrl,
                            Long themeId) {
        // Constructor to quickly create a fully-configured link.
        this.admin = admin;
        this.project = project;
        this.licenseId = licenseId;
        this.validFrom = validFrom;
        this.endTo = endTo;
        this.status = status;
        this.slug = slug;
        this.appName = appName;
        this.apkUrl = apkUrl;
        this.themeId = themeId;
    }

    public AdminUserProject(AdminUser admin,
                            Project project,
                            String licenseId,
                            LocalDate validFrom,
                            LocalDate endTo) {
        // Convenience constructor that creates an ACTIVE link with other optional fields null.
        this(admin, project, licenseId, validFrom, endTo, "ACTIVE", null, null, null, null);
    }

    @PrePersist
    protected void onCreate() {
        // Automatically set timestamps when inserting a new row.
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        // Automatically update updatedAt whenever entity is updated.
        this.updatedAt = LocalDateTime.now();
    }

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUser getAdmin() { return admin; }
    public void setAdmin(AdminUser admin) { this.admin = admin; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getEndTo() { return endTo; }
    public void setEndTo(LocalDate endTo) { this.endTo = endTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getApkUrl() { return apkUrl; }
    public void setApkUrl(String apkUrl) { this.apkUrl = apkUrl; }

    public String getIpaUrl() { return ipaUrl; }
    public void setIpaUrl(String ipaUrl) { this.ipaUrl = ipaUrl; }

    public String getBundleUrl() { return bundleUrl; }
    public void setBundleUrl(String bundleUrl) { this.bundleUrl = bundleUrl; }

    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    // Convenience computed values (not persisted) useful for JSON output or service logic.

    @Transient public Long getAdminId() { return admin != null ? admin.getAdminId() : null; }
    @Transient public Long getProjectId() { return project != null ? project.getId() : null; }

    // Status helper methods so callers don't repeat string comparisons.
    @Transient public boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }
    @Transient public boolean isSuspended() { return "SUSPENDED".equalsIgnoreCase(status); }
    @Transient public boolean isExpired() { return "EXPIRED".equalsIgnoreCase(status); }
    @Transient public boolean isDeleted() { return "DELETED".equalsIgnoreCase(status); }
}
