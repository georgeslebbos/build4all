package com.build4all.admin.dto;

import java.time.LocalDate;

/**
 * Request DTO used when creating/updating an AdminUserProject assignment (an "app" under a project for an owner).
 *
 * This DTO is typically sent from the admin dashboard UI to the backend.
 * The service layer (AdminUserProjectService.assign) uses these fields to:
 * - link an admin to a project (projectId)
 * - set app metadata (appName, slug, themeId, currencyCode)
 * - set licensing and validity dates (licenseId, validFrom, endTo)
 */
public class AdminAppAssignmentRequest {

    // Required: which Project this app assignment belongs to.
    private Long projectId;

    // Optional: display name shown in UI for this app instance.
    private String appName;

    // Optional: URL-friendly identifier for routing/tenant discovery.
    // If not provided, the service generates it from appName using slugify().
    // ensureUniqueSlug() will enforce uniqueness within (ownerId, projectId).
    private String slug;

    // Optional: external/internal license identifier. If missing, the service may generate one.
    private String licenseId;

    // Optional: start date for validity/subscription.
    private LocalDate validFrom;

    // Optional: end date for validity/subscription.
    private LocalDate endTo;

    // Optional: selected theme for this app instance (nullable => fallback theme).
    private Long themeId;

    // Optional: currency code to set the Currency relation for this app instance (e.g., "USD", "SAR", "LBP").
    // The service loads Currency by code and assigns it to AdminUserProject.currency.
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
