package com.build4all.admin.domain;

import com.build4all.project.domain.Project;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "admin_user_projects",
        uniqueConstraints = @UniqueConstraint(name="uk_aup_admin_project", columnNames={"admin_id","project_id"}),
        indexes = {
                @Index(name="idx_aup_admin", columnList="admin_id"),
                @Index(name="idx_aup_project", columnList="project_id")
        }
)
public class AdminUserProject {

    @EmbeddedId
    private AdminUserProjectId id = new AdminUserProjectId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("adminId")
    @JoinColumn(name = "admin_id", referencedColumnName = "admin_id")
    private AdminUser admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", referencedColumnName = "id")
    private Project project;

    /** Extra fields requested */
    @Column(name = "license_id")
    private String licenseId;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "end_to")
    private LocalDate endTo;

    public AdminUserProject() {}

    public AdminUserProject(AdminUser admin, Project project, String licenseId, LocalDate validFrom, LocalDate endTo) {
        this.admin = admin;
        this.project = project;
        this.id = new AdminUserProjectId(admin.getAdminId(), project.getId());
        this.licenseId = licenseId;
        this.validFrom = validFrom;
        this.endTo = endTo;
    }

    public AdminUserProjectId getId() { return id; }
    public void setId(AdminUserProjectId id) { this.id = id; }

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
}
