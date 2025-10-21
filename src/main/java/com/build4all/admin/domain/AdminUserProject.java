package com.build4all.admin.domain;

import com.build4all.project.domain.Project;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "admin_user_projects",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_aup_admin_project", columnNames = {"admin_id", "project_id"}
    ),
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
    private AdminUser admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", referencedColumnName = "id", nullable = false)
    private Project project;

    @Column(name = "license_id")
    private String licenseId;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "end_to")
    private LocalDate endTo;

    @Column(name = "status", length = 16)
    private String status = "ACTIVE";

    @Column(name = "slug", length = 128)
    private String slug;

    @Column(name = "apk_url", columnDefinition = "TEXT")
    private String apkUrl;

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
                            String apkUrl) {
        this.admin = admin;
        this.project = project;
        this.licenseId = licenseId;
        this.validFrom = validFrom;
        this.endTo = endTo;
        this.status = status;
        this.slug = slug;
        this.apkUrl = apkUrl;
    }

    public AdminUserProject(AdminUser admin,
                            Project project,
                            String licenseId,
                            LocalDate validFrom,
                            LocalDate endTo) {
        this(admin, project, licenseId, validFrom, endTo, "ACTIVE", null, null);
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = this.createdAt; }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

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

    public String getApkUrl() { return apkUrl; }
    public void setApkUrl(String apkUrl) { this.apkUrl = apkUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Transient public Long getAdminId() { return admin != null ? admin.getAdminId() : null; }
    @Transient public Long getProjectId() { return project != null ? project.getId() : null; }
}
