package com.build4all.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuperAdminAppRowDto {

    private final Long linkId;
    private final Long ownerId;
    private final String ownerUsername;

    private final Long projectId;
    private final String projectName;

    private final String slug;
    private final String appName;
    private final String status;

    private final String androidPackageName;
    private final String androidVersionName;
    private final Number androidVersionCode;
    private final String apkUrl;
    private final String bundleUrl;

    private final String iosBundleId;
    private final String iosVersionName;
    private final Number iosBuildNumber;
    private final String ipaUrl;

    // keep as Object to accept LocalDate OR LocalDateTime without constructor mismatch
    private final Object validFrom;
    private final Object endTo;

    // ✅ IMPORTANT: constructor signature MUST match JPQL "new ...(...)"
    public SuperAdminAppRowDto(
            Long linkId,
            Long ownerId,
            String ownerUsername,
            Long projectId,
            String projectName,
            String slug,
            String appName,
            Object status,                 // can be Enum or String
            String androidPackageName,
            String androidVersionName,
            Number androidVersionCode,     // Integer/Long safe
            String apkUrl,
            String bundleUrl,
            String iosBundleId,
            String iosVersionName,
            Number iosBuildNumber,         // Integer/Long safe
            String ipaUrl,
            Object validFrom,              // LocalDate/LocalDateTime safe
            Object endTo
    ) {
        this.linkId = linkId;
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername;

        this.projectId = projectId;
        this.projectName = projectName;

        this.slug = slug;
        this.appName = appName;
        this.status = status == null ? null : status.toString();

        this.androidPackageName = androidPackageName;
        this.androidVersionName = androidVersionName;
        this.androidVersionCode = androidVersionCode;
        this.apkUrl = apkUrl;
        this.bundleUrl = bundleUrl;

        this.iosBundleId = iosBundleId;
        this.iosVersionName = iosVersionName;
        this.iosBuildNumber = iosBuildNumber;
        this.ipaUrl = ipaUrl;

        this.validFrom = validFrom;
        this.endTo = endTo;
    }

    public Long getLinkId() { return linkId; }
    public Long getOwnerId() { return ownerId; }
    public String getOwnerUsername() { return ownerUsername; }
    public Long getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public String getSlug() { return slug; }
    public String getAppName() { return appName; }
    public String getStatus() { return status; }

    public String getAndroidPackageName() { return androidPackageName; }
    public String getAndroidVersionName() { return androidVersionName; }
    public Number getAndroidVersionCode() { return androidVersionCode; }
    public String getApkUrl() { return apkUrl; }
    public String getBundleUrl() { return bundleUrl; }

    public String getIosBundleId() { return iosBundleId; }
    public String getIosVersionName() { return iosVersionName; }
    public Number getIosBuildNumber() { return iosBuildNumber; }
    public String getIpaUrl() { return ipaUrl; }

    public Object getValidFrom() { return validFrom; }
    public Object getEndTo() { return endTo; }
}
