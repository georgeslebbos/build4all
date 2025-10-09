package com.build4all.admin.dto;

import java.time.LocalDate;

public class AdminProjectAssignmentRequest {
    private Long projectId;       // required for create
    private String licenseId;     // optional
    private LocalDate validFrom;  // optional
    private LocalDate endTo;      // optional

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getEndTo() { return endTo; }
    public void setEndTo(LocalDate endTo) { this.endTo = endTo; }
}
