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
    private String logoUrl; // <— new

    public AdminAppAssignmentResponse(Long projectId, String projectName,
                                      String appName, String slug, String status, String licenseId,
                                      LocalDate validFrom, LocalDate endTo,
                                      Long themeId, String apkUrl, String logoUrl) {
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
        this.logoUrl = logoUrl;
    }

    // getters only if you like immutability, or add setters if needed
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
    public String getLogoUrl() { return logoUrl; }
}
