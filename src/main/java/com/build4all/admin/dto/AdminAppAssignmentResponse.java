// src/main/java/com/build4all/admin/dto/AdminAppAssignmentResponse.java
package com.build4all.admin.dto;

import java.time.LocalDate;

public class AdminAppAssignmentResponse {
    private Long projectId;
    private String projectName;
    private String appName;
    private String slug;
    private String status;
    private String licenseId;
    private LocalDate validFrom;
    private LocalDate endTo;
    private Long themeId;
    private String apkUrl;
    private String ipaUrl;
    private String bundleUrl; // <— NEW
    private String logoUrl;

    public AdminAppAssignmentResponse(
            Long projectId, String projectName, String appName, String slug, String status,
            String licenseId, LocalDate validFrom, LocalDate endTo, Long themeId,
            String apkUrl, String ipaUrl, String bundleUrl, String logoUrl) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.appName = appName;
        this.slug = slug;
        this.status = status;
        this.licenseId = licenseId;
        this.validFrom = validFrom;
        this.endTo = endTo;
        this.themeId = themeId;
        this.apkUrl = apkUrl;
        this.ipaUrl = ipaUrl;
        this.bundleUrl = bundleUrl;
        this.logoUrl = logoUrl;
    }

    // getters only (or add setters if you need)
    public Long getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public String getAppName() { return appName; }
    public String getSlug() { return slug; }
    public String getStatus() { return status; }
    public String getLicenseId() { return licenseId; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getEndTo() { return endTo; }
    public Long getThemeId() { return themeId; }
    public String getApkUrl() { return apkUrl; }
    public String getIpaUrl() { return ipaUrl; }
    public String getBundleUrl() { return bundleUrl; }
    public String getLogoUrl() { return logoUrl; }
}
