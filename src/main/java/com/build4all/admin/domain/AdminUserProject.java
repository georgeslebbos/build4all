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
                @UniqueConstraint(name = "uk_aup_owner_project_slug", columnNames = {"admin_id", "project_id", "slug"})
        },
        indexes = {
                @Index(name = "idx_aup_admin", columnList = "admin_id"),
                @Index(name = "idx_aup_project", columnList = "project_id"),
                @Index(name = "idx_aup_slug", columnList = "slug")
        }
)
public class AdminUserProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aup_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", referencedColumnName = "admin_id", nullable = false)
    @JsonIgnore
    private AdminUser admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", referencedColumnName = "id", nullable = false)
    @JsonIgnore
    private Project project;

    @Column(name = "license_id")
    private String licenseId;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "end_to")
    private LocalDate endTo;

    @Column(name = "status", length = 16)
    private String status = "ACTIVE"; // ACTIVE | SUSPENDED | EXPIRED | DELETED

    @Column(name = "slug", length = 128)
    private String slug;

    @Column(name = "app_name", length = 128)
    private String appName;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "apk_url", columnDefinition = "TEXT")
    private String apkUrl;

    @Column(name = "ipa_url", columnDefinition = "TEXT")
    private String ipaUrl;

    @Column(name = "bundle_url", columnDefinition = "TEXT")
    private String bundleUrl;

    @Column(name = "theme_id")
    private Long themeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", referencedColumnName = "currency_id")
    private Currency currency;

    @Column(name = "android_version_code")
    private Integer androidVersionCode;

    @Column(name = "android_version_name", length = 32)
    private String androidVersionName;

    @Column(name = "android_package_name", length = 255, unique = true)
    private String androidPackageName;
    
    @Column(name = "ios_bundle_id", length = 255, unique = true)
    private String iosBundleId;

    @Column(name = "ios_build_number")
    private Integer iosBuildNumber;

    @Column(name = "ios_version_name", length = 32)
    private String iosVersionName;


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
        this(admin, project, licenseId, validFrom, endTo, "ACTIVE", null, null, null, null);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---------------- FIX: generate package name ONCE ----------------
    /**
     * Ensure Android package name exists.
     * Strategy: com.build4all.opl{linkId}
     * Must be called ONLY after entity has an ID.
     */
    public String ensureAndroidPackageName() {
        if (this.androidPackageName == null || this.androidPackageName.isBlank()) {
            if (this.id == null) {
                throw new IllegalStateException("Cannot generate androidPackageName before ID exists");
            }
            this.androidPackageName = "com.build4all.opl" + this.id;
        }
        return this.androidPackageName;
    }

    /**
     * Bump Android version:
     * - If first time: versionCode=1, versionName="1.0.0"
     * - Else: increment patch (1.0.0 -> 1.0.1)
     */
    public void bumpAndroidVersion() {
        if (this.androidVersionCode == null) {
            this.androidVersionCode = 1;
        } else {
            this.androidVersionCode = this.androidVersionCode + 1;
        }

        if (this.androidVersionName == null || this.androidVersionName.isBlank()) {
            this.androidVersionName = "1.0.0";
            return;
        }

        String[] parts = this.androidVersionName.split("\\.");
        try {
            int lastIndex = parts.length - 1;
            int patch = Integer.parseInt(parts[lastIndex]);
            patch++;
            parts[lastIndex] = String.valueOf(patch);
            this.androidVersionName = String.join(".", parts);
        } catch (Exception e) {
            this.androidVersionName = "1.0.0";
        }
    }
    
    
    public String ensureIosBundleId() {
        if (this.iosBundleId == null || this.iosBundleId.isBlank()) {
            if (this.id == null) {
                throw new IllegalStateException("Cannot generate iosBundleId before ID exists");
            }
        
            this.iosBundleId = "com.build4all.opl" + this.id + ".app";
        }
        return this.iosBundleId;
    }


    public void bumpIosVersion() {
        if (this.iosBuildNumber == null) this.iosBuildNumber = 1;
        else this.iosBuildNumber = this.iosBuildNumber + 1;

        if (this.iosVersionName == null || this.iosVersionName.isBlank()) {
            this.iosVersionName = "1.0.0";
            return;
        }

        String[] parts = this.iosVersionName.split("\\.");
        try {
            int last = parts.length - 1;
            int patch = Integer.parseInt(parts[last]);
            patch++;
            parts[last] = String.valueOf(patch);
            this.iosVersionName = String.join(".", parts);
        } catch (Exception e) {
            this.iosVersionName = "1.0.0";
        }
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

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getApkUrl() { return apkUrl; }
    public void setApkUrl(String apkUrl) { this.apkUrl = apkUrl; }

    public String getIpaUrl() { return ipaUrl; }
    public void setIpaUrl(String ipaUrl) { this.ipaUrl = ipaUrl; }

    public String getBundleUrl() { return bundleUrl; }
    public void setBundleUrl(String bundleUrl) { this.bundleUrl = bundleUrl; }

    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public Integer getAndroidVersionCode() { return androidVersionCode; }
    public void setAndroidVersionCode(Integer androidVersionCode) { this.androidVersionCode = androidVersionCode; }

    public String getAndroidVersionName() { return androidVersionName; }
    public void setAndroidVersionName(String androidVersionName) { this.androidVersionName = androidVersionName; }

    public String getAndroidPackageName() { return androidPackageName; }
    public void setAndroidPackageName(String androidPackageName) { this.androidPackageName = androidPackageName; }
    
    public String getIosBundleId() { return iosBundleId; }
    public void setIosBundleId(String iosBundleId) { this.iosBundleId = iosBundleId; }

    public Integer getIosBuildNumber() { return iosBuildNumber; }
    public void setIosBuildNumber(Integer iosBuildNumber) { this.iosBuildNumber = iosBuildNumber; }

    public String getIosVersionName() { return iosVersionName; }
    public void setIosVersionName(String iosVersionName) { this.iosVersionName = iosVersionName; }


    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    

    @Transient public Long getAdminId() { return admin != null ? admin.getAdminId() : null; }
    @Transient public Long getProjectId() { return project != null ? project.getId() : null; }

    @Transient public boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }
    @Transient public boolean isSuspended() { return "SUSPENDED".equalsIgnoreCase(status); }
    @Transient public boolean isExpired() { return "EXPIRED".equalsIgnoreCase(status); }
    @Transient public boolean isDeleted() { return "DELETED".equalsIgnoreCase(status); }
    
    
    
    
}
