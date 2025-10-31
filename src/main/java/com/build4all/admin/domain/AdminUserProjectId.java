package com.build4all.admin.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AdminUserProjectId implements Serializable {

    private Long adminId;
    private Long projectId;

    public AdminUserProjectId() {}
    public AdminUserProjectId(Long adminId, Long projectId) {
        this.adminId = adminId;
        this.projectId = projectId;
    }

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminUserProjectId that)) return false;
        return Objects.equals(adminId, that.adminId) &&
               Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adminId, projectId);
    }
}
