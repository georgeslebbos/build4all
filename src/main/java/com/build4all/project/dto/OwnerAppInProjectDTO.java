package com.build4all.project.dto;

public class OwnerAppInProjectDTO {
    private Long id;        // AdminUserProject id
    private String slug;
    private String appName;
    private String status;
    private String apkUrl;
    private String ipaUrl;
    private String bundleUrl;

    public OwnerAppInProjectDTO(Long id, String slug, String appName, String status,
                                String apkUrl, String ipaUrl, String bundleUrl) {
        this.id = id;
        this.slug = slug;
        this.appName = appName;
        this.status = status;
        this.apkUrl = apkUrl;
        this.ipaUrl = ipaUrl;
        this.bundleUrl = bundleUrl;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getAppName() { return appName; }
    public String getStatus() { return status; }
    public String getApkUrl() { return apkUrl; }
    public String getIpaUrl() { return ipaUrl; }
    public String getBundleUrl() { return bundleUrl; }
}
