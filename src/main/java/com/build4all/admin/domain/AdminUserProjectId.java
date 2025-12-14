package com.build4all.admin.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
/**
 * Composite key (Embeddable) used with @EmbeddedId in an entity that represents
 * the relationship between an AdminUser and a Project.
 *
 * This class contains the two IDs that together identify a unique link row.
 */
public class AdminUserProjectId implements Serializable {

    // Part 1 of the composite key: admin identifier.
    private Long adminId;

    // Part 2 of the composite key: project identifier.
    private Long projectId;

    // Default constructor required by JPA
    public AdminUserProjectId() {}

    public AdminUserProjectId(Long adminId, Long projectId) {
        // Initialize both parts of the composite key.
        this.adminId = adminId;
        this.projectId = projectId;
    }

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    @Override
    public boolean equals(Object o) {
        // Two keys are equal if they represent the same (adminId, projectId).
        if (this == o) return true;

        // Pattern matching is used here: if o is not AdminUserProjectId, return false.
        if (!(o instanceof AdminUserProjectId that)) return false;

        return Objects.equals(adminId, that.adminId) &&
                Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        // Must be consistent with equals() so the key works reliably in HashMap/HashSet.
        return Objects.hash(adminId, projectId);
    }
}
