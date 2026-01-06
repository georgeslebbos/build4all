package com.build4all.project.dto;

public class ProjectOwnerSummaryDTO {
    private Long adminId;
    private String fullName;
    private String email;
    private long appsCount;

    public ProjectOwnerSummaryDTO(Long adminId, String fullName, String email, long appsCount) {
        this.adminId = adminId;
        this.fullName = fullName;
        this.email = email;
        this.appsCount = appsCount;
    }

    public Long getAdminId() { return adminId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public long getAppsCount() { return appsCount; }
}
