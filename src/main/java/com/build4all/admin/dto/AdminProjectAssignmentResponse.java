package com.build4all.admin.dto;

import java.time.LocalDate;

public class AdminProjectAssignmentResponse {
    private Long projectId;
    private String projectName;
    private String licenseId;
    private LocalDate validFrom;
    private LocalDate endTo;

    public AdminProjectAssignmentResponse(Long projectId, String projectName,
                                          String licenseId, LocalDate validFrom, LocalDate endTo) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.licenseId = licenseId;
        this.validFrom = validFrom;
        this.endTo = endTo;
    }

    public Long getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public String getLicenseId() { return licenseId; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getEndTo() { return endTo; }
}
