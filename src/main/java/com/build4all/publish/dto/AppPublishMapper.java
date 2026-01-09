package com.build4all.publish.dto;

import com.build4all.publish.domain.AppPublishRequest;

public class AppPublishMapper {

    private AppPublishMapper() {}

    public static AppPublishRequestResponseDto toDto(AppPublishRequest r) {
        AppPublishRequestResponseDto dto = new AppPublishRequestResponseDto();

        dto.setId(r.getId());

        if (r.getAdminUserProject() != null) {
            dto.setAupId(r.getAdminUserProject().getId());
        }

        dto.setPlatform(r.getPlatform());
        dto.setStore(r.getStore());
        dto.setStatus(r.getStatus());

        if (r.getRequestedBy() != null) dto.setRequestedByAdminId(r.getRequestedBy().getAdminId());
        if (r.getReviewedBy() != null) dto.setReviewedByAdminId(r.getReviewedBy().getAdminId());

        dto.setRequestedAt(r.getRequestedAt());
        dto.setReviewedAt(r.getReviewedAt());
        dto.setPublishedAt(r.getPublishedAt());
        dto.setAdminNotes(r.getAdminNotes());

        dto.setApplicationName(r.getApplicationName());
        dto.setPackageNameSnapshot(r.getPackageNameSnapshot());
        dto.setBundleIdSnapshot(r.getBundleIdSnapshot());

        dto.setShortDescription(r.getShortDescription());
        dto.setFullDescription(r.getFullDescription());

        dto.setCategory(r.getCategory());
        dto.setCountryAvailability(r.getCountryAvailability());

        dto.setPricing(r.getPricing());
        dto.setContentRatingConfirmed(r.isContentRatingConfirmed());

        dto.setAppIconUrl(r.getAppIconUrl());
        dto.setScreenshotsUrlsJson(r.getScreenshotsUrlsJson());

        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());

        return dto;
    }
}
