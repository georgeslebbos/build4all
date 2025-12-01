package com.build4all.admin.dto;

import java.time.LocalDate;

public class AdminAppAssignmentRequest {
    private Long projectId;      // required
    private String appName;      // optional (display)
    private String slug;         // optional; auto from appName; ensureUniqueSlug() will enforce uniqueness
    private String licenseId;    // optional
    private LocalDate validFrom; // optional
    private LocalDate endTo;     // optional
    private Long themeId;        // optional
    private String currencyCode;
    
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getEndTo() { return endTo; }
    public void setEndTo(LocalDate endTo) { this.endTo = endTo; }
    public Long getThemeId() { return themeId; }
    public void setThemeId(Long themeId) { this.themeId = themeId; }
}
