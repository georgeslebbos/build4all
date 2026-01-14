package com.build4all.publish.dto;

import com.build4all.publish.domain.*;

import java.time.LocalDateTime;

public class AppPublishRequestAdminDto {
    private Long id;

    private Long aupId;
    private String appName;

    private PublishPlatform platform;
    private PublishStore store;
    private PublishStatus status;

    private Long requestedByAdminId;
    private Long reviewedByAdminId;

    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;

    private String packageNameSnapshot;
    private String bundleIdSnapshot;

    private String shortDescription;
    private String fullDescription;
    private String category;

    private PricingType pricing;
    private Boolean contentRatingConfirmed;

    private String appIconUrl;
    private String screenshotsUrlsJson;

    private String adminNotes;

    private PublisherProfileDto publisherProfile;
    
 // from AUP
    private Integer androidVersionCode;
    private String androidVersionName;

    private Integer iosBuildNumber;
    private String iosVersionName;
    
 // --- AUP build artifacts ---
    private String apkUrl;
    private String bundleUrl;   // AAB
    private String ipaUrl;
    private String logoUrl;

    // --- identity ---
    private String requestedByName;
    private String reviewedByName;

    // --- timestamps ---
    private LocalDateTime updatedAt;     // request last update
    private LocalDateTime submittedAt;   // if exists



   

    public String getApkUrl() {
		return apkUrl;
	}
	public void setApkUrl(String apkUrl) {
		this.apkUrl = apkUrl;
	}
	public String getBundleUrl() {
		return bundleUrl;
	}
	public void setBundleUrl(String bundleUrl) {
		this.bundleUrl = bundleUrl;
	}
	public String getIpaUrl() {
		return ipaUrl;
	}
	public void setIpaUrl(String ipaUrl) {
		this.ipaUrl = ipaUrl;
	}
	public String getLogoUrl() {
		return logoUrl;
	}
	public void setLogoUrl(String logoUrl) {
		this.logoUrl = logoUrl;
	}
	public String getRequestedByName() {
		return requestedByName;
	}
	public void setRequestedByName(String requestedByName) {
		this.requestedByName = requestedByName;
	}
	public String getReviewedByName() {
		return reviewedByName;
	}
	public void setReviewedByName(String reviewedByName) {
		this.reviewedByName = reviewedByName;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	public LocalDateTime getSubmittedAt() {
		return submittedAt;
	}
	public void setSubmittedAt(LocalDateTime submittedAt) {
		this.submittedAt = submittedAt;
	}
	public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAupId() { return aupId; }
    public void setAupId(Long aupId) { this.aupId = aupId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public PublishPlatform getPlatform() { return platform; }
    public void setPlatform(PublishPlatform platform) { this.platform = platform; }

    public PublishStore getStore() { return store; }
    public void setStore(PublishStore store) { this.store = store; }

    public PublishStatus getStatus() { return status; }
    public void setStatus(PublishStatus status) { this.status = status; }

    public Long getRequestedByAdminId() { return requestedByAdminId; }
    public void setRequestedByAdminId(Long requestedByAdminId) { this.requestedByAdminId = requestedByAdminId; }

    public Long getReviewedByAdminId() { return reviewedByAdminId; }
    public void setReviewedByAdminId(Long reviewedByAdminId) { this.reviewedByAdminId = reviewedByAdminId; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

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

    public PricingType getPricing() { return pricing; }
    public void setPricing(PricingType pricing) { this.pricing = pricing; }

    public Boolean getContentRatingConfirmed() { return contentRatingConfirmed; }
    public void setContentRatingConfirmed(Boolean contentRatingConfirmed) { this.contentRatingConfirmed = contentRatingConfirmed; }

    public String getAppIconUrl() { return appIconUrl; }
    public void setAppIconUrl(String appIconUrl) { this.appIconUrl = appIconUrl; }

    public String getScreenshotsUrlsJson() { return screenshotsUrlsJson; }
    public void setScreenshotsUrlsJson(String screenshotsUrlsJson) { this.screenshotsUrlsJson = screenshotsUrlsJson; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public PublisherProfileDto getPublisherProfile() { return publisherProfile; }
    public void setPublisherProfile(PublisherProfileDto publisherProfile) { this.publisherProfile = publisherProfile; }
	public Integer getAndroidVersionCode() {
		return androidVersionCode;
	}
	public void setAndroidVersionCode(Integer androidVersionCode) {
		this.androidVersionCode = androidVersionCode;
	}
	public String getAndroidVersionName() {
		return androidVersionName;
	}
	public void setAndroidVersionName(String androidVersionName) {
		this.androidVersionName = androidVersionName;
	}
	public Integer getIosBuildNumber() {
		return iosBuildNumber;
	}
	public void setIosBuildNumber(Integer iosBuildNumber) {
		this.iosBuildNumber = iosBuildNumber;
	}
	public String getIosVersionName() {
		return iosVersionName;
	}
	public void setIosVersionName(String iosVersionName) {
		this.iosVersionName = iosVersionName;
	}
    
    
}
