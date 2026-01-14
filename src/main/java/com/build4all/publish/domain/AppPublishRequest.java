package com.build4all.publish.domain;



import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_publish_request",
        indexes = {
                @Index(name = "idx_publish_aup", columnList = "admin_user_project_id"),
                @Index(name = "idx_publish_status", columnList = "status")
        }
)
public class AppPublishRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // App link (AdminUserProject)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_project_id", referencedColumnName = "aup_id", nullable = false)
    @JsonIgnore
    private AdminUserProject adminUserProject;

    // Global publisher profile (superadmin store account)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_profile_id", nullable = false)
    @JsonIgnore 
    private StorePublisherProfile publisherProfile;


    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private PublishPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "store", nullable = false, length = 20)
    private PublishStore store;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PublishStatus status = PublishStatus.DRAFT;

    // Who requested / who reviewed (admins)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_admin_id", referencedColumnName = "admin_id", nullable = false)
    private AdminUser requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id", referencedColumnName = "admin_id")
    private AdminUser reviewedBy;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // Step 1
    @Column(name = "application_name", nullable = false, length = 128)
    private String applicationName;

    @Column(name = "package_name_snapshot", length = 255)
    private String packageNameSnapshot;

    @Column(name = "bundle_id_snapshot", length = 255)
    private String bundleIdSnapshot;

    @Column(name = "short_description", nullable = false, length = 80)
    private String shortDescription;

    @Column(name = "full_description", nullable = false, columnDefinition = "TEXT")
    private String fullDescription;

    // Step 2
    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "country_availability", length = 100)
    private String countryAvailability;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing", nullable = false, length = 10)
    private PricingType pricing = PricingType.FREE;

    @Column(name = "content_rating_confirmed", nullable = false)
    private boolean contentRatingConfirmed = false;

    // Step 4
    @Column(name = "app_icon_url", columnDefinition = "TEXT")
    private String appIconUrl;

    // store JSON array as TEXT (simple + safe even if jsonb exists)
    // later we can map as jsonb properly, but this is stable and fast.
    @Column(name = "screenshots_urls", nullable = false, columnDefinition = "TEXT")
    private String screenshotsUrlsJson = "[]";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // getters/setters (generate quickly from IDE)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUserProject getAdminUserProject() { return adminUserProject; }
    public void setAdminUserProject(AdminUserProject adminUserProject) { this.adminUserProject = adminUserProject; }

    public StorePublisherProfile getPublisherProfile() { return publisherProfile; }
    public void setPublisherProfile(StorePublisherProfile publisherProfile) { this.publisherProfile = publisherProfile; }

    public PublishPlatform getPlatform() { return platform; }
    public void setPlatform(PublishPlatform platform) { this.platform = platform; }

    public PublishStore getStore() { return store; }
    public void setStore(PublishStore store) { this.store = store; }

    public PublishStatus getStatus() { return status; }
    public void setStatus(PublishStatus status) { this.status = status; }

    public AdminUser getRequestedBy() { return requestedBy; }
    public void setRequestedBy(AdminUser requestedBy) { this.requestedBy = requestedBy; }

    public AdminUser getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(AdminUser reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public String getPackageNameSnapshot() { return packageNameSnapshot; }
    public void setPackageNameSnapshot(String packageNameSnapshot) { this.packageNameSnapshot = packageNameSnapshot; }

    public String getBundleIdSnapshot() { return bundleIdSnapshot; }
    public void setBundleIdSnapshot(String bundleIdSnapshot) { this.bundleIdSnapshot = bundleIdSnapshot; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getFullDescription() { return fullDescription; }
    public void setFullDescription(String fullDescription) { this.fullDescription = fullDescription; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCountryAvailability() { return countryAvailability; }
    public void setCountryAvailability(String countryAvailability) { this.countryAvailability = countryAvailability; }

    public PricingType getPricing() { return pricing; }
    public void setPricing(PricingType pricing) { this.pricing = pricing; }

    public boolean isContentRatingConfirmed() { return contentRatingConfirmed; }
    public void setContentRatingConfirmed(boolean contentRatingConfirmed) { this.contentRatingConfirmed = contentRatingConfirmed; }

    public String getAppIconUrl() { return appIconUrl; }
    public void setAppIconUrl(String appIconUrl) { this.appIconUrl = appIconUrl; }

    public String getScreenshotsUrlsJson() { return screenshotsUrlsJson; }
    public void setScreenshotsUrlsJson(String screenshotsUrlsJson) {
        this.screenshotsUrlsJson = (screenshotsUrlsJson == null || screenshotsUrlsJson.isBlank()) ? "[]" : screenshotsUrlsJson;
    }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

  
}

