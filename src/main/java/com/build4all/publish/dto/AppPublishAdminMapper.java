package com.build4all.publish.dto;

import com.build4all.publish.domain.AppPublishRequest;
import com.build4all.publish.domain.StorePublisherProfile;

public class AppPublishAdminMapper {

    private AppPublishAdminMapper() {}

    public static AppPublishRequestAdminDto toDto(AppPublishRequest r) {
        AppPublishRequestAdminDto dto = new AppPublishRequestAdminDto();
        dto.setId(r.getId());

        if (r.getAdminUserProject() != null) {
            dto.setAupId(r.getAdminUserProject().getId());
            dto.setAppName(r.getAdminUserProject().getAppName());
        }

        dto.setPlatform(r.getPlatform());
        dto.setStore(r.getStore());
        dto.setStatus(r.getStatus());

        if (r.getRequestedBy() != null) dto.setRequestedByAdminId(r.getRequestedBy().getAdminId());
        if (r.getReviewedBy() != null) dto.setReviewedByAdminId(r.getReviewedBy().getAdminId());

        dto.setRequestedAt(r.getRequestedAt());
        dto.setReviewedAt(r.getReviewedAt());

        dto.setPackageNameSnapshot(r.getPackageNameSnapshot());
        dto.setBundleIdSnapshot(r.getBundleIdSnapshot());

        dto.setShortDescription(r.getShortDescription());
        dto.setFullDescription(r.getFullDescription());
        dto.setCategory(r.getCategory());

        dto.setPricing(r.getPricing());
        dto.setContentRatingConfirmed(r.isContentRatingConfirmed());

        dto.setAppIconUrl(r.getAppIconUrl());
        dto.setScreenshotsUrlsJson(r.getScreenshotsUrlsJson());

        dto.setAdminNotes(r.getAdminNotes());

        StorePublisherProfile p = r.getPublisherProfile();
        if (p != null) {
            PublisherProfileDto pd = new PublisherProfileDto();
            pd.setId(p.getId());
            pd.setStore(p.getStore());
            pd.setDeveloperName(p.getDeveloperName());
            pd.setDeveloperEmail(p.getDeveloperEmail());
            pd.setPrivacyPolicyUrl(p.getPrivacyPolicyUrl());
            dto.setPublisherProfile(pd);
        }

        return dto;
    }
}
