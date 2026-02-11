package com.build4all.app.domain;

import com.build4all.admin.domain.AdminUserProject;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_build_jobs")
public class AppBuildJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aup_id", nullable = false)
    private AdminUserProject app;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BuildPlatform platform;

    @Column(name = "ci_build_id", length = 128)
    private String ciBuildId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BuildJobStatus status = BuildJobStatus.QUEUED;

    // Android meta
    private Integer androidVersionCode;
    @Column(length = 32)
    private String androidVersionName;
    @Column(length = 255)
    private String androidPackageName;

    // iOS meta
    private Integer iosBuildNumber;
    @Column(length = 32)
    private String iosVersionName;
    @Column(length = 255)
    private String iosBundleId;

    // Artifacts
    @Column(columnDefinition = "TEXT")
    private String apkUrl;

    @Column(columnDefinition = "TEXT")
    private String bundleUrl; // = AAB URL

    @Column(columnDefinition = "TEXT")
    private String ipaUrl;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = BuildJobStatus.QUEUED;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // getters/setters

    public Long getId() { return id; }

    public AdminUserProject getApp() { return app; }
    public void setApp(AdminUserProject app) { this.app = app; }

    public BuildPlatform getPlatform() { return platform; }
    public void setPlatform(BuildPlatform platform) { this.platform = platform; }

    public String getCiBuildId() { return ciBuildId; }
    public void setCiBuildId(String ciBuildId) { this.ciBuildId = ciBuildId; }

    public BuildJobStatus getStatus() { return status; }
    public void setStatus(BuildJobStatus status) { this.status = status; }

    public Integer getAndroidVersionCode() { return androidVersionCode; }
    public void setAndroidVersionCode(Integer androidVersionCode) { this.androidVersionCode = androidVersionCode; }

    public String getAndroidVersionName() { return androidVersionName; }
    public void setAndroidVersionName(String androidVersionName) { this.androidVersionName = androidVersionName; }

    public String getAndroidPackageName() { return androidPackageName; }
    public void setAndroidPackageName(String androidPackageName) { this.androidPackageName = androidPackageName; }

    public Integer getIosBuildNumber() { return iosBuildNumber; }
    public void setIosBuildNumber(Integer iosBuildNumber) { this.iosBuildNumber = iosBuildNumber; }

    public String getIosVersionName() { return iosVersionName; }
    public void setIosVersionName(String iosVersionName) { this.iosVersionName = iosVersionName; }

    public String getIosBundleId() { return iosBundleId; }
    public void setIosBundleId(String iosBundleId) { this.iosBundleId = iosBundleId; }

    public String getApkUrl() { return apkUrl; }
    public void setApkUrl(String apkUrl) { this.apkUrl = apkUrl; }

    public String getBundleUrl() { return bundleUrl; }
    public void setBundleUrl(String bundleUrl) { this.bundleUrl = bundleUrl; }

    public String getIpaUrl() { return ipaUrl; }
    public void setIpaUrl(String ipaUrl) { this.ipaUrl = ipaUrl; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
