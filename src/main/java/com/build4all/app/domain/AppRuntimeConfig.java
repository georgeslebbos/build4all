package com.build4all.app.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_runtime_config")
public class AppRuntimeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One config per app (AdminUserProject)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aup_id", referencedColumnName = "aup_id", nullable = false, unique = true)
    @JsonIgnore
    private AdminUserProject app;

    @Column(name = "nav_json", columnDefinition = "TEXT")
    private String navJson;

    @Column(name = "home_json", columnDefinition = "TEXT")
    private String homeJson;

    @Column(name = "enabled_features_json", columnDefinition = "TEXT")
    private String enabledFeaturesJson;

    @Column(name = "branding_json", columnDefinition = "TEXT")
    private String brandingJson;

    @Column(name = "api_base_url_override", columnDefinition = "TEXT")
    private String apiBaseUrlOverride;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // getters/setters
    public Long getId() { return id; }

    public AdminUserProject getApp() { return app; }
    public void setApp(AdminUserProject app) { this.app = app; }

    public String getNavJson() { return navJson; }
    public void setNavJson(String navJson) { this.navJson = navJson; }

    public String getHomeJson() { return homeJson; }
    public void setHomeJson(String homeJson) { this.homeJson = homeJson; }

    public String getEnabledFeaturesJson() { return enabledFeaturesJson; }
    public void setEnabledFeaturesJson(String enabledFeaturesJson) { this.enabledFeaturesJson = enabledFeaturesJson; }

    public String getBrandingJson() { return brandingJson; }
    public void setBrandingJson(String brandingJson) { this.brandingJson = brandingJson; }

    public String getApiBaseUrlOverride() { return apiBaseUrlOverride; }
    public void setApiBaseUrlOverride(String apiBaseUrlOverride) { this.apiBaseUrlOverride = apiBaseUrlOverride; }
}
